package com.dnd.smartflow.common.crud;

import com.dnd.smartflow.common.persistence.TenantEntity;
import org.mapstruct.*;

@MapperConfig(componentModel = "spring", mappingInheritanceStrategy = MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CopierConfig {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void base(TenantEntity source, @MappingTarget TenantEntity target);
}
