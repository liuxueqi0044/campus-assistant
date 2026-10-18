package cn.iocoder.yudao.module.campus.media.controller.app.vo;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CompleteMediaRequest {

    @Positive
    private long generation;
}
