package com.example.lifetrack.domain.repository

/**
 * 兼容聚合接口。新代码应依赖最小职责接口；保留该接口用于现有实现与测试平滑迁移。
 */
interface TrackRepository :
    RecordRepository,
    StatisticsRepository,
    TypeCatalogRepository,
    BackupRepository,
    DatabaseMaintenanceRepository
