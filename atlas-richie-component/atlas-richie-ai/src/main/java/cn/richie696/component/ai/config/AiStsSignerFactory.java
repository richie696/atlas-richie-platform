/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.config;

import cn.richie696.component.ai.api.voicechat.StsTicket;
import cn.richie696.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import cn.richie696.component.ai.support.sign.AkSkHmacStsSigner;
import cn.richie696.component.ai.support.sign.AppCodeStsSigner;
import cn.richie696.component.ai.support.sign.BearerStsSigner;
import cn.richie696.component.ai.support.sign.StsSigner;
import cn.richie696.component.ai.support.sign.Tc3StsSigner;
import cn.richie696.component.ai.support.sign.XApiKeyStsSigner;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * STS 签名器的唯一构造入口，供启动期 Bean 与 Secret 运行期候选代际共同使用。
 */
public final class AiStsSignerFactory {

    private AiStsSignerFactory() {
    }

    public static List<StsSigner> createAll(AiModelProperties properties) {
        List<StsSigner> signers = new ArrayList<>();
        AbstractAudioModelConfig zhipu = config(properties.getTts(), "zhipu");
        if (has(zhipu, zhipu == null ? null : zhipu.getApiKey())) {
            signers.add(zhipu(properties));
        }
        AbstractAudioModelConfig dashscope = config(properties.getTts(), "dashscope");
        if (has(dashscope, dashscope == null ? null : dashscope.getApiKey())) {
            signers.add(dashscope(properties));
        }
        AbstractAudioModelConfig hunyuanTts = config(properties.getTts(), "hunyuan");
        if (has(hunyuanTts, hunyuanTts == null ? null : hunyuanTts.getApiKey())) {
            signers.add(hunyuanTokenHub(properties));
        }
        if (hasPair(hunyuanTts,
                hunyuanTts == null ? null : hunyuanTts.getSecretId(),
                hunyuanTts == null ? null : hunyuanTts.getSecretKey())) {
            signers.add(hunyuanTts(properties));
        }
        AbstractAudioModelConfig hunyuanStt = config(properties.getStt(), "hunyuan");
        if (hasPair(hunyuanStt,
                hunyuanStt == null ? null : hunyuanStt.getSecretId(),
                hunyuanStt == null ? null : hunyuanStt.getSecretKey())) {
            signers.add(hunyuanStt(properties));
        }
        AbstractAudioModelConfig pangu = config(properties.getTts(), "pangu");
        if (has(pangu, pangu == null ? null : pangu.getAppCode())) {
            signers.add(pangu(properties));
        }
        AbstractAudioModelConfig doubao = config(properties.getTts(), "doubao");
        if (has(doubao, doubao == null ? null : doubao.getApiKey())) {
            signers.add(doubaoOpenspeech(properties));
        }
        if (hasPair(doubao,
                doubao == null ? null : doubao.getApiKey(),
                doubao == null ? null : doubao.getSecretKey())) {
            signers.add(doubaoVikingdb(properties));
        }
        return List.copyOf(signers);
    }

    public static StsSigner zhipu(AiModelProperties properties) {
        AbstractAudioModelConfig c = required(properties.getTts(), "zhipu");
        return new BearerStsSigner(StsTicket.VENDOR_ZHIPU, c.getApiKey(),
                valueOr(c.getBaseUrl(), "https://open.bigmodel.cn/api/paas/v4/realtime"));
    }

    public static StsSigner dashscope(AiModelProperties properties) {
        AbstractAudioModelConfig c = required(properties.getTts(), "dashscope");
        return new BearerStsSigner(StsTicket.VENDOR_DASHSCOPE, c.getApiKey(),
                valueOr(c.getBaseUrl(), "wss://dashscope.aliyuncs.com/api-ws/v1/realtime"));
    }

    public static StsSigner hunyuanTokenHub(AiModelProperties properties) {
        AbstractAudioModelConfig c = required(properties.getTts(), "hunyuan");
        return new BearerStsSigner(StsTicket.VENDOR_HUNYUAN_TOKENHUB, c.getApiKey(),
                valueOr(c.getBaseUrl(), "wss://hunyuan.tencent.com/v3/realtime"));
    }

    public static StsSigner hunyuanTts(AiModelProperties properties) {
        AbstractAudioModelConfig c = required(properties.getTts(), "hunyuan");
        return new Tc3StsSigner(StsTicket.VENDOR_HUNYUAN_TTS, c.getSecretId(), c.getSecretKey(),
                valueOr(c.getRegion(), "ap-guangzhou"), "tts",
                valueOr(c.getEndpoint(), "tts.tencentcloudapi.com"));
    }

    public static StsSigner hunyuanStt(AiModelProperties properties) {
        AbstractAudioModelConfig c = required(properties.getStt(), "hunyuan");
        return new Tc3StsSigner(StsTicket.VENDOR_HUNYUAN_STT, c.getSecretId(), c.getSecretKey(),
                valueOr(c.getRegion(), "ap-guangzhou"), "asr",
                valueOr(c.getEndpoint(), "asr.tencentcloudapi.com"));
    }

    public static StsSigner pangu(AiModelProperties properties) {
        AbstractAudioModelConfig c = required(properties.getTts(), "pangu");
        return new AppCodeStsSigner(StsTicket.VENDOR_PANGU, c.getAppCode(),
                valueOr(c.getBaseUrl(), "https://pangu.apigw.com/v1/realtime"));
    }

    public static StsSigner doubaoOpenspeech(AiModelProperties properties) {
        AbstractAudioModelConfig c = required(properties.getTts(), "doubao");
        return new XApiKeyStsSigner(StsTicket.VENDOR_DOUBAO_OPENSPEECH, c.getApiKey(),
                c.getAppId(), c.getResourceId(),
                valueOr(c.getBaseUrl(), "wss://openspeech.bytedance.com/api/v3/tts/bidirection"));
    }

    public static StsSigner doubaoVikingdb(AiModelProperties properties) {
        AbstractAudioModelConfig c = required(properties.getTts(), "doubao");
        return new AkSkHmacStsSigner(StsTicket.VENDOR_DOUBAO_VIKINGDB,
                c.getApiKey(), c.getSecretKey(), valueOr(c.getRegion(), "cn-north-1"),
                "vikingdb", valueOr(c.getBaseUrl(), "https://vikingdb.volcengineapi.com"));
    }

    private static boolean has(AbstractAudioModelConfig config, String value) {
        return config != null && StringUtils.hasText(value);
    }

    private static boolean hasPair(AbstractAudioModelConfig config, String first, String second) {
        return config != null && StringUtils.hasText(first) && StringUtils.hasText(second);
    }

    private static AbstractAudioModelConfig required(
            Map<String, ? extends AbstractAudioModelConfig> configs,
            String name) {
        AbstractAudioModelConfig config = config(configs, name);
        if (config == null) {
            throw new IllegalStateException("Missing AI audio configuration: " + name);
        }
        return config;
    }

    private static AbstractAudioModelConfig config(
            Map<String, ? extends AbstractAudioModelConfig> configs,
            String name) {
        return configs == null ? null : configs.get(name);
    }

    private static String valueOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
