package cn.richie696.component.secret.provider.kmip;

import cn.richie696.component.secret.api.*; import cn.richie696.component.secret.api.crypto.*; import cn.richie696.component.secret.api.exception.*; import cn.richie696.component.secret.api.provider.*; import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties; import cn.richie696.component.secret.bootstrap.spi.*;
import org.springframework.boot.context.properties.bind.Binder; import org.springframework.core.env.ConfigurableEnvironment;
import javax.net.ssl.*; import java.io.*; import java.net.*; import java.nio.ByteBuffer; import java.nio.ByteOrder; import java.nio.file.Files; import java.nio.file.Path; import java.security.KeyStore; import java.util.*; import java.util.concurrent.atomic.AtomicBoolean;

/** KMIP 2.1 TLS session. Only protocol Encrypt/Decrypt is exposed as wrap/unwrap. */
public final class KmipSecretClient implements SecretBootstrapClient, KeyWrappingBackend, SecretProviderSession {
    private static final int REQUEST_MESSAGE=0x420078, REQUEST_HEADER=0x420077, PROTOCOL_VERSION=0x420069, MAJOR=0x42006a, MINOR=0x42006b, BATCH_COUNT=0x42000d, BATCH_ITEM=0x42000f, OPERATION=0x42005c, REQUEST_PAYLOAD=0x420079, UNIQUE_IDENTIFIER=0x420094, DATA=0x4200c2, CRYPTO_PARAMETERS=0x42002b, CRYPTO_ALGORITHM=0x420028, BLOCK_CIPHER_MODE=0x420011;
    private static final int RESPONSE_MESSAGE=0x42007b, RESPONSE_PAYLOAD=0x42007c, RESULT_STATUS=0x42007f, RESULT_SUCCESS=0;
    private static final Set<SecretCapability> CAPABILITIES=Set.of(SecretCapability.KEY_WRAP,SecretCapability.KEY_UNWRAP);
    private final String providerId, hash; private final KmipSecretProperties properties; private final BootstrapSecretProperties bootstrap; private final SSLSocketFactory socketFactory; private final AtomicBoolean closed=new AtomicBoolean();
    private KmipSecretClient(String id,String hash,KmipSecretProperties p,BootstrapSecretProperties b,SSLSocketFactory socketFactory){providerId=id;this.hash=hash;properties=p;bootstrap=b;this.socketFactory=socketFactory;}
    static KmipSecretClient create(ConfigurableEnvironment env,BootstrapSecretProperties b){return create(env,b,null);} static KmipSecretClient create(ConfigurableEnvironment env,BootstrapSecretProperties b,SecretBootstrapContext context){String prefix=KmipSecretProperties.PREFIX,id="kmip";if(context!=null&&context.providerId()!=null&&!context.providerId().isBlank()){id=context.providerId();if(context.configurationPrefix()!=null&&!context.configurationPrefix().isBlank())prefix=context.configurationPrefix();}else{String active=b.getActiveProvider();if(active!=null&&!active.isBlank()&&b.getProviders().containsKey(active)){prefix=BootstrapSecretProperties.PREFIX+".providers."+active;id=active;}}KmipSecretProperties p=Binder.get(env).bind(prefix,KmipSecretProperties.class).orElseGet(KmipSecretProperties::new);KmipSecretConfiguration.validate(p);try{return new KmipSecretClient(id,KmipSecretConfiguration.hash(id,p),p,b,sslSocketFactory(p));}catch(SecretException e){throw e;}catch(Exception e){throw new SecretConfigurationException("SEC-BOOT-003","KMIP TLS context cannot be created",e);}}
    @Override public SecretBootstrapResult load(SecretBootstrapRequest request){ensure();return new SecretBootstrapResult(providerId,"kms-only",String.join(",",request.logicalPaths()),java.time.Instant.now(),Map.of(),null);}
    @Override public WrappedKey wrap(KeyReference ref,byte[] plaintext,CryptoContext context){ensure();if(plaintext==null||plaintext.length==0)throw new SecretCryptoException("SEC-CRYPTO-001","Plaintext data key must not be empty");byte[] result=operation(31,ref,plaintext);return new WrappedKey(result,"kmip-aes-kwp");}
    @Override public byte[] unwrap(KeyReference ref,WrappedKey wrapped,CryptoContext context){ensure();if(!"kmip-aes-kwp".equals(wrapped.algorithm()))throw new SecretCryptoException("SEC-CRYPTO-002","Wrapped key algorithm is not supported by KMIP");byte[] data=wrapped.value();try{return operation(32,ref,data);}finally{Arrays.fill(data,(byte)0);}}
    @Override public SecretProviderDescriptor descriptor(){return new SecretProviderDescriptor("kmip",providerId,CAPABILITIES);} @Override public Optional<SecretBackend> secretBackend(){return Optional.empty();} @Override public Optional<KeyWrappingBackend> keyWrappingBackend(){return Optional.of(this);} String configurationHash(){return hash;} @Override public void close(){closed.set(true);}
    private byte[] operation(int op,KeyReference ref,byte[] data){byte[] body=null,response=null;try{body=KmipTtlv.structure(REQUEST_MESSAGE,KmipTtlv.structure(REQUEST_HEADER,KmipTtlv.structure(PROTOCOL_VERSION,KmipTtlv.integer(MAJOR,properties.getProtocolMajor()),KmipTtlv.integer(MINOR,properties.getProtocolMinor())),KmipTtlv.integer(BATCH_COUNT,1)),KmipTtlv.structure(BATCH_ITEM,KmipTtlv.enumeration(OPERATION,op),KmipTtlv.structure(REQUEST_PAYLOAD,KmipTtlv.text(UNIQUE_IDENTIFIER,key(ref)),KmipTtlv.structure(CRYPTO_PARAMETERS,KmipTtlv.enumeration(BLOCK_CIPHER_MODE,12),KmipTtlv.enumeration(CRYPTO_ALGORITHM,3)),KmipTtlv.bytes(DATA,data))));response=send(body);return parseResponse(op,response);}catch(SecretException e){throw e;}catch(Exception e){throw new SecretCryptoException("SEC-CRYPTO-001","KMIP operation failed",e);}finally{if(body!=null)Arrays.fill(body,(byte)0);if(response!=null)Arrays.fill(response,(byte)0);}}
    static byte[] parseResponse(int expectedOperation,byte[] response){List<KmipTtlv.Element> root=KmipTtlv.children(response);KmipTtlv.Element message=single(root,RESPONSE_MESSAGE,KmipTtlv.STRUCTURE,"Response Message");List<KmipTtlv.Element> messageChildren=KmipTtlv.children(message.value());KmipTtlv.Element batch=single(messageChildren,BATCH_ITEM,KmipTtlv.STRUCTURE,"Batch Item");List<KmipTtlv.Element> batchChildren=KmipTtlv.children(batch.value());int operation=enumeration(single(batchChildren,OPERATION,KmipTtlv.ENUMERATION,"Operation"),"Operation");if(operation!=expectedOperation)throw protocol("KMIP response operation does not match request");int status=enumeration(single(batchChildren,RESULT_STATUS,KmipTtlv.ENUMERATION,"Result Status"),"Result Status");if(status!=RESULT_SUCCESS)throw new SecretCryptoException("SEC-PROVIDER-001","KMIP operation failed with result status "+status);KmipTtlv.Element payload=single(batchChildren,RESPONSE_PAYLOAD,KmipTtlv.STRUCTURE,"Response Payload");KmipTtlv.Element data=single(KmipTtlv.children(payload.value()),DATA,KmipTtlv.BYTE_STRING,"Data");return data.value().clone();}
    private static KmipTtlv.Element single(List<KmipTtlv.Element> elements,int tag,int type,String label){KmipTtlv.Element match=null;for(KmipTtlv.Element element:elements){if(element.tag()==tag&&element.type()==type){if(match!=null)throw protocol("KMIP response contains multiple "+label+" elements");match=element;}}if(match==null)throw protocol("KMIP response is missing "+label);return match;}
    private static int enumeration(KmipTtlv.Element element,String label){if(element.value().length!=4)throw protocol("KMIP "+label+" has invalid length");return ByteBuffer.wrap(element.value()).order(ByteOrder.BIG_ENDIAN).getInt();}
    private static SecretCryptoException protocol(String message){return new SecretCryptoException("SEC-PROVIDER-001",message);}
    private byte[] send(byte[] request) throws Exception {
        return retryIo(bootstrap.getResilience().getMaxAttempts(), () -> sendOnce(request));
    }

    private byte[] sendOnce(byte[] request) throws IOException {
        URI endpoint = properties.getEndpoint();
        SSLSocket socket = connectSocket(
                socketFactory,
                endpoint,
                bootstrap.getResilience().getConnectTimeout(),
                bootstrap.getResilience().getReadTimeout());
        try {
            socket.startHandshake();
            OutputStream out = socket.getOutputStream();
            out.write(request);
            out.flush();
            InputStream input = socket.getInputStream();
            byte[] header = readFully(input, 8);
            byte[] body = null;
            try {
                int length = java.nio.ByteBuffer.wrap(header, 4, 4).getInt();
                if (length < 0 || length > 16 * 1024 * 1024) {
                    throw new IOException("KMIP response length is invalid");
                }
                int padded = length + ((8 - (length % 8)) % 8);
                body = readFully(input, padded);
                byte[] received = new byte[8 + padded];
                System.arraycopy(header, 0, received, 0, 8);
                System.arraycopy(body, 0, received, 8, padded);
                return received;
            } finally {
                Arrays.fill(header, (byte) 0);
                if (body != null) Arrays.fill(body, (byte) 0);
            }
        } finally {
            socket.close();
        }
    }

    static SSLSocket connectSocket(
            SSLSocketFactory factory,
            URI endpoint,
            java.time.Duration connectTimeout,
            java.time.Duration readTimeout) throws IOException {
        int port = endpoint.getPort() > 0 ? endpoint.getPort() : 5696;
        SSLSocket socket = (SSLSocket) factory.createSocket();
        try {
            socket.connect(new InetSocketAddress(endpoint.getHost(), port), timeoutMillis(connectTimeout));
            socket.setSoTimeout(timeoutMillis(readTimeout));
            return socket;
        } catch (IOException failure) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    static <T> T retryIo(int configuredAttempts, IoOperation<T> operation) throws IOException {
        int attempts = Math.max(1, configuredAttempts);
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return operation.execute();
            } catch (SSLHandshakeException | SSLPeerUnverifiedException permanent) {
                throw permanent;
            } catch (IOException transientFailure) {
                last = transientFailure;
                if (attempt == attempts) throw transientFailure;
            }
        }
        throw last == null ? new IOException("KMIP request failed") : last;
    }

    private static int timeoutMillis(java.time.Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) return 1;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis()));
    }

    @FunctionalInterface
    interface IoOperation<T> {
        T execute() throws IOException;
    }
    private static byte[] readFully(InputStream input,int length)throws IOException{byte[] result=new byte[length];int offset=0;while(offset<length){int n=input.read(result,offset,length-offset);if(n<0)throw new EOFException("KMIP response ended before the TTLV value was complete");offset+=n;}return result;}
    private static SSLSocketFactory sslSocketFactory(KmipSecretProperties p)throws Exception{TrustManager[] trust=null;KeyManager[] keys=null;if(p.getTrustStore()!=null&&!p.getTrustStore().isBlank()){KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());char[] password=p.getTrustStorePassword();try(var input=Files.newInputStream(Path.of(p.getTrustStore()))){store.load(input,password);}finally{Arrays.fill(password,'\0');}TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(store);trust=factory.getTrustManagers();}if(p.getKeyStore()!=null&&!p.getKeyStore().isBlank()){KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());char[] password=p.getKeyStorePassword();try(var input=Files.newInputStream(Path.of(p.getKeyStore()))){store.load(input,password);KeyManagerFactory factory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());factory.init(store,password);keys=factory.getKeyManagers();}finally{Arrays.fill(password,'\0');}}SSLContext context=SSLContext.getInstance("TLS");context.init(keys,trust,null);return context.getSocketFactory();}
    private String key(KeyReference ref){return properties.getKeyBindings().getOrDefault(ref.logicalKey(),ref.logicalKey());} private void ensure(){if(closed.get())throw new IllegalStateException("KMIP Secret Provider is closed");}
}
