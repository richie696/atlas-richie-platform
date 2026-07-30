package cn.richie696.antivirus.model;

import cn.richie696.contract.model.BaseStreamMessage;
import lombok.Data;

import java.io.Serializable;

/** Redis Stream 的扫描任务消息；具体下载地址保存在短期任务记录中。 */
@Data
public class ScanRequestedEvent implements BaseStreamMessage, Serializable {
    private String taskId;
}
