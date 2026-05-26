package com.richie.component.storage.bean;

import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.transfer.TransferManager;

import java.io.File;
import java.io.Serializable;

public record DownloadContext(
        TransferManager transferManager,
        GetObjectRequest request,
        File targetFile
) implements Serializable {
}
