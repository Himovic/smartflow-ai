package com.dnd.smartflow.common.crud;

import com.dnd.smartflow.ai.domain.AiPrediction;
import com.dnd.smartflow.assets.domain.Equipment;
import com.dnd.smartflow.assets.domain.MaintenanceWorkOrder;
import com.dnd.smartflow.catalog.domain.Customer;
import com.dnd.smartflow.catalog.domain.Product;
import com.dnd.smartflow.catalog.domain.Supplier;
import com.dnd.smartflow.config.domain.CustomFieldDefinition;
import com.dnd.smartflow.config.domain.KpiDefinition;
import com.dnd.smartflow.distribution.domain.Delivery;
import com.dnd.smartflow.distribution.domain.SalesOrder;
import com.dnd.smartflow.distribution.domain.SalesOrderLine;
import com.dnd.smartflow.fleet.domain.Driver;
import com.dnd.smartflow.fleet.domain.FuelLog;
import com.dnd.smartflow.fleet.domain.RoutePlan;
import com.dnd.smartflow.fleet.domain.Vehicle;
import com.dnd.smartflow.fleet.domain.VehiclePosition;
import com.dnd.smartflow.integration.domain.IntegrationIdMap;
import com.dnd.smartflow.integration.domain.SyncJob;
import com.dnd.smartflow.integration.domain.SyncRecord;
import com.dnd.smartflow.inventory.domain.Batch;
import com.dnd.smartflow.inventory.domain.StockLevel;
import com.dnd.smartflow.inventory.domain.StockMovement;
import com.dnd.smartflow.inventory.domain.Warehouse;
import com.dnd.smartflow.notification.domain.Notification;
import com.dnd.smartflow.notification.domain.NotificationRule;
import com.dnd.smartflow.organization.domain.Department;
import com.dnd.smartflow.organization.domain.Employee;
import com.dnd.smartflow.organization.domain.ProductionLine;
import com.dnd.smartflow.organization.domain.Site;
import com.dnd.smartflow.qhse.domain.Audit;
import com.dnd.smartflow.qhse.domain.AuditFinding;
import com.dnd.smartflow.qhse.domain.CapaAction;
import com.dnd.smartflow.qhse.domain.Certification;
import com.dnd.smartflow.qhse.domain.ChemicalProduct;
import com.dnd.smartflow.qhse.domain.CleaningRecord;
import com.dnd.smartflow.qhse.domain.CleaningSchedule;
import com.dnd.smartflow.qhse.domain.EnvironmentalReading;
import com.dnd.smartflow.qhse.domain.Incident;
import com.dnd.smartflow.qhse.domain.Inspection;
import com.dnd.smartflow.qhse.domain.InspectionTemplate;
import com.dnd.smartflow.qhse.domain.NonConformity;
import com.dnd.smartflow.qhse.domain.PestControlVisit;
import com.dnd.smartflow.qhse.domain.PpeIssuance;
import com.dnd.smartflow.qhse.domain.PpeItem;
import com.dnd.smartflow.qhse.domain.QhseDocument;
import com.dnd.smartflow.qhse.domain.RiskAssessment;
import com.dnd.smartflow.qhse.domain.Training;
import com.dnd.smartflow.qhse.domain.TrainingRecord;
import com.dnd.smartflow.qhse.domain.WasteRecord;
import com.dnd.smartflow.qhse.domain.WorkPermit;
import com.dnd.smartflow.workflow.domain.Attachment;
import com.dnd.smartflow.workflow.domain.Comment;
import com.dnd.smartflow.workflow.domain.WorkflowDefinition;
import com.dnd.smartflow.workflow.domain.WorkflowEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * MapStruct copier used by the generic CRUD layer. It copies only business attributes from an
 * incoming payload onto a managed entity: identity, tenant, audit and version fields are never
 * overwritten (mass-assignment protection), and workflow-managed status/reference fields can only
 * change through the workflow engine.
 */
@Mapper(config = CopierConfig.class)
public interface EntityCopier {

    void copy(Site source, @MappingTarget Site target);

    void copy(Department source, @MappingTarget Department target);

    void copy(ProductionLine source, @MappingTarget ProductionLine target);

    void copy(Employee source, @MappingTarget Employee target);

    void copy(Equipment source, @MappingTarget Equipment target);

    void copy(MaintenanceWorkOrder source, @MappingTarget MaintenanceWorkOrder target);

    void copy(Supplier source, @MappingTarget Supplier target);

    void copy(Customer source, @MappingTarget Customer target);

    void copy(Product source, @MappingTarget Product target);

    void copy(Warehouse source, @MappingTarget Warehouse target);

    @Mapping(target = "status", ignore = true)
    void copy(Batch source, @MappingTarget Batch target);

    void copy(StockLevel source, @MappingTarget StockLevel target);

    void copy(StockMovement source, @MappingTarget StockMovement target);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    void copy(SalesOrder source, @MappingTarget SalesOrder target);

    void copy(SalesOrderLine source, @MappingTarget SalesOrderLine target);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "deliveredAt", ignore = true)
    @Mapping(target = "departedAt", ignore = true)
    @Mapping(target = "co2Kg", ignore = true)
    @Mapping(target = "fuelLiters", ignore = true)
    @Mapping(target = "delayMinutes", ignore = true)
    @Mapping(target = "deliveryNumber", ignore = true)
    void copy(Delivery source, @MappingTarget Delivery target);

    void copy(Vehicle source, @MappingTarget Vehicle target);

    void copy(Driver source, @MappingTarget Driver target);

    void copy(FuelLog source, @MappingTarget FuelLog target);

    void copy(VehiclePosition source, @MappingTarget VehiclePosition target);

    void copy(RoutePlan source, @MappingTarget RoutePlan target);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reference", ignore = true)
    void copy(NonConformity source, @MappingTarget NonConformity target);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reference", ignore = true)
    void copy(CapaAction source, @MappingTarget CapaAction target);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reference", ignore = true)
    void copy(Incident source, @MappingTarget Incident target);

    void copy(InspectionTemplate source, @MappingTarget InspectionTemplate target);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "score", ignore = true)
    @Mapping(target = "result", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "generatedNcCount", ignore = true)
    @Mapping(target = "reference", ignore = true)
    void copy(Inspection source, @MappingTarget Inspection target);

    void copy(RiskAssessment source, @MappingTarget RiskAssessment target);

    void copy(WorkPermit source, @MappingTarget WorkPermit target);

    void copy(Audit source, @MappingTarget Audit target);

    void copy(AuditFinding source, @MappingTarget AuditFinding target);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "approverName", ignore = true)
    void copy(QhseDocument source, @MappingTarget QhseDocument target);

    void copy(Training source, @MappingTarget Training target);

    void copy(TrainingRecord source, @MappingTarget TrainingRecord target);

    void copy(Certification source, @MappingTarget Certification target);

    void copy(PpeItem source, @MappingTarget PpeItem target);

    void copy(PpeIssuance source, @MappingTarget PpeIssuance target);

    void copy(CleaningSchedule source, @MappingTarget CleaningSchedule target);

    void copy(CleaningRecord source, @MappingTarget CleaningRecord target);

    void copy(PestControlVisit source, @MappingTarget PestControlVisit target);

    void copy(EnvironmentalReading source, @MappingTarget EnvironmentalReading target);

    void copy(WasteRecord source, @MappingTarget WasteRecord target);

    void copy(ChemicalProduct source, @MappingTarget ChemicalProduct target);

    void copy(CustomFieldDefinition source, @MappingTarget CustomFieldDefinition target);

    void copy(KpiDefinition source, @MappingTarget KpiDefinition target);

    void copy(WorkflowDefinition source, @MappingTarget WorkflowDefinition target);

    void copy(WorkflowEvent source, @MappingTarget WorkflowEvent target);

    void copy(Comment source, @MappingTarget Comment target);

    void copy(Attachment source, @MappingTarget Attachment target);

    void copy(Notification source, @MappingTarget Notification target);

    void copy(NotificationRule source, @MappingTarget NotificationRule target);

    void copy(SyncJob source, @MappingTarget SyncJob target);

    void copy(SyncRecord source, @MappingTarget SyncRecord target);

    void copy(IntegrationIdMap source, @MappingTarget IntegrationIdMap target);

    void copy(AiPrediction source, @MappingTarget AiPrediction target);
}
