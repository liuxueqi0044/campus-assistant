package cn.iocoder.yudao.module.campus.identity.controller.app.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Cursor pagination is keyset based; null nextCursor means there is no next page. */
@Data
@AllArgsConstructor
public class SchoolDirectoryResponse {

    private List<SchoolPublicResponse> items;
    private String nextCursor;
}
