package cn.iocoder.yudao.module.campus.media.controller.app.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UploadTicketRequest {

    @NotBlank
    @Size(max = 48)
    private String purpose;

    @NotBlank
    @Size(max = 255)
    private String fileName;

    @Positive
    private long size;

    @NotBlank
    @Size(max = 127)
    private String mime;
}
