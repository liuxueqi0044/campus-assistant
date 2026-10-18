package cn.iocoder.yudao.module.campus.common.api;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * M00.02 分页结果：字段名固定为 {@code list/total/pageNo/pageSize}。
 */
@Data
public class CampusPageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> list;

    /** 总数；调用方不得用 {@code list.size()} 推断 */
    private Long total;

    /** 当前页码 */
    private Integer pageNo;

    /** 当前每页条数 */
    private Integer pageSize;

    public static <T> CampusPageResult<T> of(List<T> list, long total, CampusPageParam param) {
        CampusPageResult<T> result = new CampusPageResult<>();
        result.list = list;
        result.total = total;
        result.pageNo = param.getPageNo();
        result.pageSize = param.getPageSize();
        return result;
    }

}
