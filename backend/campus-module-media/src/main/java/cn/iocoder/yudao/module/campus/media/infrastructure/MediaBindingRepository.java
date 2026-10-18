package cn.iocoder.yudao.module.campus.media.infrastructure;

import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaBindingDO;
import cn.iocoder.yudao.module.campus.media.infrastructure.dal.mysql.MediaBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MediaBindingRepository {

    private final MediaBindingMapper mapper;

    public MediaBindingDO findByAssetId(long assetId) {
        return mapper.selectByAssetId(assetId);
    }

    public List<MediaBindingDO> findByAssetIds(List<Long> assetIds) {
        return assetIds.isEmpty() ? List.of() : mapper.selectByAssetIds(List.copyOf(assetIds));
    }

    public List<MediaBindingDO> findByResource(String resourceType, String resourceId) {
        return mapper.selectByResource(resourceType, resourceId);
    }

    public void insert(MediaBindingDO binding) {
        mapper.insertBinding(binding);
    }
}
