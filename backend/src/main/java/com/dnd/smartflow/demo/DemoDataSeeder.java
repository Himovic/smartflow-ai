package com.dnd.smartflow.demo;

import com.dnd.smartflow.ai.routing.HeuristicRouteSolver;
import com.dnd.smartflow.ai.routing.RoutingModels;
import com.dnd.smartflow.assets.domain.*;
import com.dnd.smartflow.catalog.domain.*;
import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.persistence.TenantRepository;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.config.domain.*;
import com.dnd.smartflow.distribution.domain.*;
import com.dnd.smartflow.fleet.domain.*;
import com.dnd.smartflow.fleet.service.EmissionCalculator;
import com.dnd.smartflow.iam.User;
import com.dnd.smartflow.iam.UserRepository;
import com.dnd.smartflow.integration.domain.IntegrationConnector;
import com.dnd.smartflow.integration.domain.IntegrationConnectorRepository;
import com.dnd.smartflow.integration.sync.SyncEngine;
import com.dnd.smartflow.inventory.domain.*;
import com.dnd.smartflow.notification.domain.Notification;
import com.dnd.smartflow.notification.domain.NotificationRepository;
import com.dnd.smartflow.notification.domain.NotificationRule;
import com.dnd.smartflow.notification.domain.NotificationRuleRepository;
import com.dnd.smartflow.organization.domain.*;
import com.dnd.smartflow.qhse.domain.*;
import com.dnd.smartflow.qhse.service.ChecklistScorer;
import com.dnd.smartflow.qhse.service.RiskMatrix;
import com.dnd.smartflow.tenant.*;
import com.dnd.smartflow.workflow.WorkflowInstaller;
import com.dnd.smartflow.workflow.domain.WorkflowEvent;
import com.dnd.smartflow.workflow.domain.WorkflowEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.dnd.smartflow.demo.DemoReferenceData.*;

/**
 * Builds the coherent fictional dataset of "Atlas Industries Group" (FMCG, Algeria): organization,
 * catalog, 18 months of production batches, orders, deliveries and fleet activity, and 12 months of QHSE
 * records whose workflows, histories and KPIs are internally consistent. Deterministic (fixed seed).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {
    public static final String TENANT_CODE = "ATLAS";
    public static final String PASSWORD = "Demo@2026!";
    static final ZoneId ZONE = ZoneId.of("Africa/Algiers");

    private final com.dnd.smartflow.tenant.TenantRepository tenants;
    private final TenantService tenantService;
    private final WorkflowInstaller workflowInstaller;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SiteRepository sites;
    private final DepartmentRepository departments;
    private final ProductionLineRepository lines;
    private final EmployeeRepository employees;
    private final EquipmentRepository equipment;
    private final MaintenanceWorkOrderRepository workOrders;
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final BatchRepository batches;
    private final StockLevelRepository stockLevels;
    private final StockMovementRepository movements;
    private final SalesOrderRepository orders;
    private final SalesOrderLineRepository orderLines;
    private final DeliveryRepository deliveries;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final FuelLogRepository fuelLogs;
    private final RoutePlanRepository routePlans;
    private final InspectionTemplateRepository templates;
    private final InspectionRepository inspections;
    private final NonConformityRepository ncs;
    private final CapaActionRepository capas;
    private final IncidentRepository incidents;
    private final RiskAssessmentRepository risks;
    private final WorkPermitRepository permits;
    private final AuditRepository audits;
    private final AuditFindingRepository findings;
    private final QhseDocumentRepository documents;
    private final TrainingRepository trainings;
    private final TrainingRecordRepository trainingRecords;
    private final CertificationRepository certifications;
    private final PpeItemRepository ppeItems;
    private final PpeIssuanceRepository ppeIssuances;
    private final CleaningScheduleRepository cleaningSchedules;
    private final CleaningRecordRepository cleaningRecords;
    private final PestControlVisitRepository pestVisits;
    private final EnvironmentalReadingRepository readings;
    private final WasteRecordRepository waste;
    private final ChemicalProductRepository chemicals;
    private final KpiDefinitionRepository kpis;
    private final CustomFieldDefinitionRepository customFields;
    private final NotificationRuleRepository notificationRules;
    private final NotificationRepository notifications;
    private final WorkflowEventRepository workflowEvents;
    private final IntegrationConnectorRepository connectors;
    private final SyncEngine syncEngine;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final com.dnd.smartflow.common.crud.ReferenceGenerator references;

    // --------------------------------------------------------------- in-memory model of the seeded data
    private Random rnd;
    private LocalDate today;
    private final Map<String, Site> siteByCode = new LinkedHashMap<>();
    private final Map<String, Department> deptByCode = new LinkedHashMap<>();
    private final Map<String, ProductionLine> lineByCode = new LinkedHashMap<>();
    private final Map<String, Warehouse> whByCode = new LinkedHashMap<>();
    private final Map<String, Product> productBySku = new LinkedHashMap<>();
    private final Map<String, ProductDef> defBySku = new LinkedHashMap<>();
    private final Map<String, Supplier> supplierByCode = new LinkedHashMap<>();
    private final List<Customer> customerList = new ArrayList<>();
    private final Map<UUID, String> customerHub = new HashMap<>();
    private final List<Employee> employeeList = new ArrayList<>();
    private final Map<String, User> userByKey = new LinkedHashMap<>();
    private final List<Equipment> equipmentList = new ArrayList<>();
    private final List<Vehicle> vehicleList = new ArrayList<>();
    private final List<Driver> driverList = new ArrayList<>();
    private final Map<String, List<Batch>> batchesBySku = new HashMap<>();
    private final Map<String, InspectionTemplate> templateByCode = new HashMap<>();

    /** Creates the demo tenant and all its data. Returns the tenant id. */
    public UUID seed() {
        rnd = new Random(2026);
        today = LocalDate.now(ZONE);
        clearModel();
        long t0 = System.currentTimeMillis();
        Tenant tenant = tx.execute(s -> {
            Tenant t = new Tenant();
            t.setId(UUID.randomUUID());
            t.setCode(TENANT_CODE);
            t.setName("Atlas Industries Group");
            t.setIndustryProfile("FMCG_MANUFACTURING");
            t.setCountry("Algérie");
            t.setTimezone("Africa/Algiers");
            t.setDefaultLanguage("fr");
            t.setDemo(true);
            return tenants.save(t);
        });
        tenantService.applyIndustryProfile(tenant.getId(), "FMCG_MANUFACTURING");
        TenantContext.runAs(tenant.getId(), () -> {
            step("configuration", this::seedConfiguration);
            step("organization", this::seedOrganization);
            step("users", this::seedUsers);
            step("catalog", this::seedCatalog);
            step("assets", this::seedAssets);
            step("fleet", this::seedFleet);
            step("production", this::seedBatches);
            step("orders & deliveries", this::seedOrdersAndDeliveries);
            step("stock", this::seedStock);
            step("route plans", this::seedRoutePlans);
            step("inspections & NC", this::seedInspectionsAndNcs);
            step("incidents", this::seedIncidents);
            step("risks & permits", this::seedRisks);
            step("audits", this::seedAudits);
            step("documents", this::seedDocuments);
            step("training", this::seedTraining);
            step("hygiene", this::seedHygiene);
            step("environment", this::seedEnvironment);
            step("integrations", this::seedIntegrations);
            step("notifications", this::seedNotifications);
        });
        references.synchronize(tenant.getId());
        TenantContext.runAs(tenant.getId(), this::runInitialSyncs);
        log.info("Demo tenant Atlas Industries Group seeded in {} s", (System.currentTimeMillis() - t0) / 1000);
        return tenant.getId();
    }

    private void step(String name, Runnable r) {
        long t = System.currentTimeMillis();
        tx.executeWithoutResult(s -> r.run());
        log.info("  demo seed: {} ({} ms)", name, System.currentTimeMillis() - t);
    }

    private void clearModel() {
        siteByCode.clear(); deptByCode.clear(); lineByCode.clear(); whByCode.clear(); productBySku.clear(); defBySku.clear();
        supplierByCode.clear(); customerList.clear(); customerHub.clear(); employeeList.clear(); userByKey.clear(); equipmentList.clear();
        vehicleList.clear(); driverList.clear(); batchesBySku.clear(); templateByCode.clear();
    }

    // =============================================================== helpers
    private Instant at(LocalDate d, int hour, int minute) {
        return d.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    private Instant randomInstant(LocalDate d) {
        return at(d, 6 + rnd.nextInt(14), rnd.nextInt(60));
    }

    private <T> T pick(List<T> list) {
        return list.get(rnd.nextInt(list.size()));
    }

    private double jitter(double v, double amplitude) {
        return v + (rnd.nextDouble() - 0.5) * 2 * amplitude;
    }

    private boolean chance(double p) {
        return rnd.nextDouble() < p;
    }

    private String personName() {
        boolean f = chance(0.35);
        return pick(f ? FIRST_NAMES_F : FIRST_NAMES_M) + " " + pick(LAST_NAMES);
    }

    private <E extends TenantEntity> List<E> saveAll(TenantRepository<E> repo, List<E> list) {
        return repo.saveAll(list);
    }

    private void event(String type, UUID id, String from, String to, String transition, String actor, Instant when, String comment) {
        WorkflowEvent e = new WorkflowEvent();
        e.setEntityType(type);
        e.setEntityId(id);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setTransition(transition);
        e.setActorName(actor);
        e.setOccurredAt(when);
        e.setComment(comment);
        workflowEvents.save(e);
    }

    private User user(String key) {
        return userByKey.get(key);
    }

    // =============================================================== configuration
    private void seedConfiguration() {
        workflowInstaller.installDefaults();
        templates.findAll().forEach(t -> templateByCode.put(t.getCode(), t));
        try {
            List<Map<String, Object>> defs = mapper.readValue(new ClassPathResource("config/kpis.json").getInputStream(), new TypeReference<>() {});
            for (Map<String, Object> d : defs) {
                KpiDefinition k = new KpiDefinition();
                k.setCode((String) d.get("code"));
                k.setName((String) d.get("name"));
                k.setDomain((String) d.get("domain"));
                k.setUnit((String) d.get("unit"));
                k.setTarget(((Number) d.get("target")).doubleValue());
                k.setDirection((String) d.get("direction"));
                k.setCalculator((String) d.get("calculator"));
                k.setActive(true);
                kpis.save(k);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        customField("NonConformity", "shift", "Équipe / shift", "SELECT", List.of("Matin", "Après-midi", "Nuit"), 1);
        customField("Incident", "witness", "Témoin", "TEXT", null, 1);
        customField("Product", "halalCertified", "Certifié halal", "BOOLEAN", null, 1);
        rule("NC_REPORTED", List.of("QUALITY_MANAGER", "QHSE_DIRECTOR"), List.of("IN_APP", "EMAIL"), "Non-conformity reported");
        rule("INCIDENT_REPORTED", List.of("SAFETY_MANAGER", "QHSE_DIRECTOR"), List.of("IN_APP", "EMAIL"), "Incident or near miss reported");
        rule("BATCH_RECALLED", List.of("QHSE_DIRECTOR", "QUALITY_MANAGER", "LOGISTICS_MANAGER", "GENERAL_MANAGER"), List.of("IN_APP", "EMAIL", "SMS"), "Product recall");
        rule("READING_OUT_OF_LIMITS", List.of("HYGIENE_MANAGER", "WAREHOUSE_MANAGER"), List.of("IN_APP"), "Sensor or reading out of limits");
        rule("CAPA_OVERDUE", List.of("QHSE_DIRECTOR"), List.of("IN_APP", "EMAIL"), "Overdue corrective action");
    }

    private void customField(String entity, String key, String label, String type, List<String> options, int order) {
        CustomFieldDefinition c = new CustomFieldDefinition();
        c.setEntityType(entity);
        c.setFieldKey(key);
        c.setLabel(label);
        c.setFieldType(type);
        c.setOptions(options);
        c.setRequired(false);
        c.setSortOrder(order);
        customFields.save(c);
    }

    private void rule(String event, List<String> roles, List<String> channels, String desc) {
        NotificationRule r = new NotificationRule();
        r.setEventType(event);
        r.setRoles(roles);
        r.setChannels(channels);
        r.setActive(true);
        r.setDescription(desc);
        notificationRules.save(r);
    }

    // =============================================================== organization
    private void seedOrganization() {
        for (SiteDef d : SITES) {
            Site s = new Site();
            s.setCode(d.code());
            s.setName(d.name());
            s.setType(d.type());
            s.setAddress(d.address());
            s.setCity(d.city());
            s.setRegion(d.region());
            s.setCountry("Algérie");
            s.setLatitude(d.lat());
            s.setLongitude(d.lon());
            s.setManagerName(d.manager());
            s.setPhone("+213 " + (20 + rnd.nextInt(19)) + " " + (100000 + rnd.nextInt(899999)));
            s.setActive(true);
            siteByCode.put(d.code(), sites.save(s));
        }
        Map<String, List<String[]>> deptDefs = Map.of(
                "PRODUCTION", List.of(new String[]{"PROD", "Production", "PRODUCTION"}, new String[]{"QUAL", "Qualité & laboratoire", "QUALITY"},
                        new String[]{"HSE", "HSE", "HSE"}, new String[]{"MAINT", "Maintenance", "MAINTENANCE"}, new String[]{"LOG", "Logistique", "LOGISTICS"},
                        new String[]{"WH", "Magasins", "WAREHOUSE"}, new String[]{"ADM", "Administration", "ADMIN"}),
                "OTHER", List.of(new String[]{"LOG", "Logistique & transport", "LOGISTICS"}, new String[]{"WH", "Entreposage", "WAREHOUSE"},
                        new String[]{"QUAL", "Hygiène & qualité", "QUALITY"}, new String[]{"HSE", "HSE", "HSE"}, new String[]{"ADM", "Administration", "ADMIN"}));
        for (Site s : siteByCode.values()) {
            for (String[] d : deptDefs.get("PRODUCTION".equals(s.getType()) ? "PRODUCTION" : "OTHER")) {
                Department dep = new Department();
                dep.setSiteId(s.getId());
                dep.setCode(s.getCode() + "-" + d[0]);
                dep.setName(d[1]);
                dep.setDomain(d[2]);
                dep.setManagerName(personName());
                deptByCode.put(dep.getCode(), departments.save(dep));
            }
        }
        String[][] lineDefs = {{"L1", "CST", "Ligne pâtes longues", "PASTA", "4200"}, {"L2", "CST", "Ligne pâtes courtes", "PASTA", "3800"},
                {"L3", "CST", "Ligne couscous & semoule", "COUSCOUS", "3000"}, {"L4", "CST", "Ligne biscuits", "BISCUITS", "2500"},
                {"L5", "STF", "Ligne papier hygiénique", "TISSUE", "5200"}, {"L6", "STF", "Ligne mouchoirs & essuie-tout", "TISSUE", "4100"},
                {"L7", "STF", "Ligne couches bébé", "DIAPERS", "1800"}};
        for (String[] l : lineDefs) {
            ProductionLine pl = new ProductionLine();
            pl.setSiteId(siteByCode.get(l[1]).getId());
            pl.setDepartmentId(deptByCode.get(l[1] + "-PROD").getId());
            pl.setCode(l[0]);
            pl.setName(l[2]);
            pl.setProductFamily(l[3]);
            pl.setCapacityPerHour(Integer.parseInt(l[4]));
            pl.setStatus(l[0].equals("L2") ? "RUNNING" : pick(List.of("RUNNING", "RUNNING", "RUNNING", "CHANGEOVER")));
            pl.setOee(Math.round(jitter(l[0].equals("L2") ? 68 : 79, 5) * 10) / 10.0);
            lineByCode.put(l[0], lines.save(pl));
        }
        Object[][] whDefs = {{"WH-CST-PF", "Entrepôt produits finis Constantine", "CST", "CENTRAL", 4500, false},
                {"WH-CST-MP", "Magasin matières premières Constantine", "CST", "PRODUCTION", 1800, false},
                {"WH-ALG-DC", "Centre de distribution Alger (Oued Smar)", "ALG", "REGIONAL", 6000, true},
                {"WH-ORN", "Entrepôt régional Oran (Es-Sénia)", "ORN", "REGIONAL", 3200, false},
                {"WH-STF", "Entrepôt produits finis Sétif", "STF", "PRODUCTION", 3800, false}};
        for (Object[] w : whDefs) {
            Site s = siteByCode.get((String) w[2]);
            Warehouse wh = new Warehouse();
            wh.setCode((String) w[0]);
            wh.setName((String) w[1]);
            wh.setSiteId(s.getId());
            wh.setType((String) w[3]);
            wh.setCity(s.getCity());
            wh.setLatitude(s.getLatitude() + 0.002);
            wh.setLongitude(s.getLongitude() + 0.002);
            wh.setCapacityPallets((Integer) w[4]);
            wh.setTemperatureControlled((Boolean) w[5]);
            if ((Boolean) w[5]) {
                wh.setMinTemp(12.0);
                wh.setMaxTemp(25.0);
            }
            wh.setManagerName(personName());
            whByCode.put(wh.getCode(), warehouses.save(wh));
        }
        String[][] jobs = {{"PROD", "Opérateur de production"}, {"PROD", "Conducteur de ligne"}, {"PROD", "Chef d'équipe production"},
                {"QUAL", "Technicien laboratoire"}, {"QUAL", "Contrôleur qualité"}, {"HSE", "Animateur HSE"}, {"MAINT", "Technicien de maintenance"},
                {"MAINT", "Électromécanicien"}, {"LOG", "Agent logistique"}, {"LOG", "Chauffeur-livreur"}, {"WH", "Cariste"}, {"WH", "Magasinier"},
                {"ADM", "Assistant administratif"}};
        int n = 0;
        for (Site s : siteByCode.values()) {
            int count = switch (s.getCode()) {
                case "CST" -> 52;
                case "STF" -> 38;
                case "ALG" -> 26;
                default -> 16;
            };
            for (int i = 0; i < count; i++) {
                String[] job = pick(Arrays.stream(jobs).filter(j -> deptByCode.containsKey(s.getCode() + "-" + j[0])).toList());
                Employee e = new Employee();
                boolean f = chance(0.3);
                e.setFirstName(pick(f ? FIRST_NAMES_F : FIRST_NAMES_M));
                e.setLastName(pick(LAST_NAMES));
                e.setMatricule("ATL-" + String.format("%05d", 1000 + (++n)));
                e.setJobTitle(job[1]);
                e.setSiteId(s.getId());
                e.setDepartmentId(deptByCode.get(s.getCode() + "-" + job[0]).getId());
                e.setEmail((e.getFirstName().charAt(0) + "." + e.getLastName()).toLowerCase(Locale.ROOT).replace(" ", "") + n + "@atlas-industries.demo");
                e.setPhone("+213 6" + (60000000 + rnd.nextInt(39999999)));
                e.setHireDate(today.minusDays(200 + rnd.nextInt(5000)));
                e.setStatus(chance(0.04) ? "ON_LEAVE" : "ACTIVE");
                employeeList.add(e);
            }
        }
        employees.saveAll(employeeList);
    }

    private void seedUsers() {
        Site cst = siteByCode.get("CST");
        Site stf = siteByCode.get("STF");
        Object[][] defs = {
                {"admin", "demo.admin@smartflowai.demo", "Yacine", "Bensalem", Role.SUPER_ADMIN, "Administrateur plateforme", null, true},
                {"qhse", "demo.qhse@smartflowai.demo", "Amina", "Khelifi", Role.QHSE_DIRECTOR, "Directrice QHSE Groupe", null, true},
                {"quality", "demo.quality@smartflowai.demo", "Karim", "Boumediene", Role.QUALITY_MANAGER, "Responsable qualité", List.of(cst.getId(), stf.getId()), false},
                {"safety", "demo.safety@smartflowai.demo", "Nadia", "Belkacem", Role.SAFETY_MANAGER, "Responsable sécurité", null, true},
                {"logistics", "demo.logistics@smartflowai.demo", "Sofiane", "Hamidi", Role.LOGISTICS_MANAGER, "Responsable logistique", null, true},
                {"employee", "demo.employee@smartflowai.demo", "Rachid", "Ouali", Role.EMPLOYEE, "Conducteur de ligne L2", List.of(cst.getId()), false},
                {"gm", "demo.gm@smartflowai.demo", "Mourad", "Benali", Role.GENERAL_MANAGER, "Directeur général", null, true},
                {"hygiene", "demo.hygiene@smartflowai.demo", "Lynda", "Saadi", Role.HYGIENE_MANAGER, "Responsable hygiène", null, true},
                {"environment", "demo.environment@smartflowai.demo", "Farid", "Zerrouki", Role.ENVIRONMENT_MANAGER, "Responsable environnement", null, true},
                {"auditor", "demo.auditor@smartflowai.demo", "Samia", "Ait Ahmed", Role.AUDITOR, "Auditrice interne", null, true}};
        String hash = encoder.encode(PASSWORD);
        for (Object[] d : defs) {
            User u = new User();
            u.setEmail((String) d[1]);
            u.setPasswordHash(hash);
            u.setFirstName((String) d[2]);
            u.setLastName((String) d[3]);
            u.setRole((Role) d[4]);
            u.setJobTitle((String) d[5]);
            @SuppressWarnings("unchecked")
            List<UUID> sitesOf = (List<UUID>) d[6];
            u.setSiteIds(sitesOf == null ? new ArrayList<>() : new ArrayList<>(sitesOf));
            u.setAllSites((Boolean) d[7]);
            u.setLanguage("fr");
            u.setActive(true);
            if ("employee".equals(d[0])) u.setDepartmentId(deptByCode.get("CST-PROD").getId());
            userByKey.put((String) d[0], users.save(u));
        }
    }

    // =============================================================== catalog
    private void seedCatalog() {
        for (SupplierDef d : SUPPLIERS) {
            Supplier s = new Supplier();
            s.setCode(d.code());
            s.setName(d.name());
            s.setCategory(d.category());
            s.setCountry(d.country());
            s.setCity(d.city());
            s.setContactName(personName());
            s.setEmail("qualite@" + d.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "") + ".demo");
            s.setPhone("+213 " + (21 + rnd.nextInt(20)) + " " + (100000 + rnd.nextInt(899999)));
            s.setStatus(d.status());
            s.setQualityScore(d.quality());
            s.setOnTimeRate(d.onTime());
            s.setCertifications(d.certs());
            s.setLastAuditDate(today.minusDays(30 + rnd.nextInt(300)));
            s.setExternalRef("SAP-BP-" + (300000 + rnd.nextInt(99999)));
            supplierByCode.put(d.code(), suppliers.save(s));
        }
        for (ProductDef d : PRODUCTS) {
            Product p = new Product();
            p.setSku(d.sku());
            p.setName(d.name());
            p.setCategory(d.category());
            p.setBrand(d.family().equals("DIAPERS") ? "Atlas Baby" : d.family().equals("TISSUE") ? "Atlas Soft" : "Atlas");
            p.setUnit(d.unit());
            p.setUnitWeightKg(d.weightKg());
            p.setShelfLifeDays(d.shelfLifeDays());
            p.setUnitPrice(BigDecimal.valueOf(d.price()));
            p.setCo2PerUnitKg(d.co2PerUnit());
            p.setMinStock(d.reorderPoint() / 3);
            p.setReorderPoint(d.reorderPoint());
            p.setBarcode("613" + String.format("%010d", Math.abs(d.sku().hashCode()) % 1_000_000_0000L));
            p.setActive(true);
            p.setExternalRef("SAP-MAT-" + d.sku());
            p.setCustomFields(Map.of("halalCertified", !d.family().equals("TISSUE") && !d.family().equals("DIAPERS")));
            productBySku.put(d.sku(), products.save(p));
            defBySku.put(d.sku(), d);
        }
        int n = 0;
        for (City c : CITIES) {
            int count = switch (c.name()) {
                case "Alger" -> 7;
                case "Oran", "Constantine" -> 5;
                case "Annaba", "Sétif", "Blida" -> 3;
                default -> 2;
            };
            for (int i = 0; i < count; i++) {
                String prefix = pick(SHOP_PREFIX);
                Customer cu = new Customer();
                cu.setCode("CLI-" + String.format("%04d", ++n));
                cu.setName(prefix + " " + pick(SHOP_NAMES) + " — " + c.name());
                cu.setType(switch (prefix) {
                    case "Grossiste", "Dépôt" -> "WHOLESALER";
                    case "Hypermarché", "Supermarché" -> "SUPERMARKET";
                    case "Distribution" -> "DISTRIBUTOR";
                    default -> "RETAILER";
                });
                cu.setCity(c.name());
                cu.setRegion(c.region());
                cu.setAddress("Cité " + pick(List.of("des 1000 logements", "El Djamel", "Ben Boulaid", "du 5 Juillet", "Sidi Mabrouk", "Les Pins", "El Feth")) + ", " + c.name());
                cu.setLatitude(Math.round(jitter(c.lat(), 0.035) * 1e5) / 1e5);
                cu.setLongitude(Math.round(jitter(c.lon(), 0.035) * 1e5) / 1e5);
                cu.setContactName(personName());
                cu.setPhone("+213 5" + (50000000 + rnd.nextInt(49999999)));
                cu.setCreditLimit(BigDecimal.valueOf(cu.getType().equals("WHOLESALER") ? 5_000_000 : 1_500_000));
                cu.setActive(true);
                cu.setExternalRef("SAP-BP-" + (100000 + n));
                Customer saved = customers.save(cu);
                customerList.add(saved);
                customerHub.put(saved.getId(), c.hub());
            }
        }
    }

    // =============================================================== assets & maintenance
    private void seedAssets() {
        Object[][] defs = {
                {"EQ-L1-PRS", "Presse à pâtes longues", "Presse / extrudeuse", "L1", "Pavan", "TP4", "HIGH", 1400.0},
                {"EQ-L1-DRY", "Séchoir pâtes longues", "Séchoir", "L1", "Pavan", "ND-2000", "HIGH", 2200.0},
                {"EQ-L1-PCK", "Ensacheuse L1", "Conditionnement", "L1", "Ricciarelli", "FSM 400", "MEDIUM", 900.0},
                {"EQ-L1-MDT", "Détecteur de métaux L1", "Détection", "L1", "Mettler Toledo", "Profile Advantage", "CRITICAL", 5000.0},
                {"EQ-L2-PRS", "Presse à pâtes courtes", "Presse / extrudeuse", "L2", "Fava", "Omnia 3200", "HIGH", 1300.0},
                {"EQ-L2-DRY", "Séchoir pâtes courtes", "Séchoir", "L2", "Fava", "TDR", "HIGH", 2000.0},
                {"EQ-L2-PCK", "Ensacheuse verticale L2", "Conditionnement", "L2", "Ricciarelli", "VFFS 250", "HIGH", 650.0},
                {"EQ-L2-MDT", "Détecteur de métaux L2", "Détection", "L2", "Mettler Toledo", "Profile Advantage", "CRITICAL", 5000.0},
                {"EQ-L3-ROL", "Rouleuse couscous", "Rouleuse", "L3", "Bühler", "CRL-3", "HIGH", 1600.0},
                {"EQ-L3-CUI", "Cuiseur vapeur couscous", "Cuiseur", "L3", "Bühler", "STC", "HIGH", 1800.0},
                {"EQ-L3-PCK", "Ensacheuse couscous", "Conditionnement", "L3", "Ishida", "Atlas 1kg", "MEDIUM", 1100.0},
                {"EQ-L4-OVN", "Four tunnel biscuits", "Four", "L4", "Baker Perkins", "Tunnel 60m", "CRITICAL", 2600.0},
                {"EQ-L4-MIX", "Pétrin biscuits", "Pétrin", "L4", "Peerless", "MX-1000", "MEDIUM", 1900.0},
                {"EQ-L4-PCK", "Emballeuse flow-pack L4", "Conditionnement", "L4", "Bosch", "Pack 301", "MEDIUM", 1200.0},
                {"EQ-L5-PM1", "Machine à papier PM1", "Machine à papier", "L5", "Andritz", "PrimeLineCOMPACT", "CRITICAL", 3000.0},
                {"EQ-L5-RWD", "Bobineuse PH", "Transformation", "L5", "Fabio Perini", "Sincro", "HIGH", 1500.0},
                {"EQ-L5-SAW", "Scie à logs", "Transformation", "L5", "Fabio Perini", "Tissue Saw", "MEDIUM", 1300.0},
                {"EQ-L6-FLD", "Plieuse mouchoirs", "Transformation", "L6", "MTC", "Facial F2", "MEDIUM", 1400.0},
                {"EQ-L6-PCK", "Emballeuse essuie-tout", "Conditionnement", "L6", "Casmatic", "CMP-4", "MEDIUM", 1200.0},
                {"EQ-L7-CNV", "Ligne de transformation couches", "Transformation", "L7", "GDM", "Baby 4.0", "CRITICAL", 1700.0},
                {"EQ-L7-SAP", "Doseur polymère SAP", "Dosage", "L7", "GDM", "SAP-Dose", "HIGH", 2100.0},
                {"EQ-CST-CMP1", "Compresseur d'air 1 — Constantine", "Utilités", null, "Atlas Copco", "GA 75", "HIGH", 4000.0},
                {"EQ-CST-BLR", "Chaudière vapeur — Constantine", "Utilités", null, "Bosch", "UL-S 6000", "CRITICAL", 6000.0},
                {"EQ-STF-CMP1", "Compresseur d'air — Sétif", "Utilités", null, "Atlas Copco", "GA 90", "HIGH", 4000.0},
                {"EQ-STF-BLR", "Chaudière vapeur — Sétif", "Utilités", null, "Viessmann", "Vitomax", "CRITICAL", 6000.0},
                {"EQ-ALG-CLD", "Groupe froid quai Alger", "Utilités", null, "Carrier", "AquaForce", "MEDIUM", 3500.0},
                {"EQ-CST-FIRE", "Système de détection incendie — Constantine", "Sécurité incendie", null, "Siemens", "Cerberus", "CRITICAL", 8000.0},
                {"EQ-STF-FIRE", "Système sprinkler — Sétif", "Sécurité incendie", null, "Tyco", "ESFR", "CRITICAL", 8000.0}};
        for (Object[] d : defs) {
            ProductionLine line = d[3] == null ? null : lineByCode.get((String) d[3]);
            String siteCode = line == null ? ((String) d[0]).substring(3, 6) : (Set.of("L5", "L6", "L7").contains(d[3]) ? "STF" : "CST");
            Equipment e = new Equipment();
            e.setCode((String) d[0]);
            e.setName((String) d[1]);
            e.setCategory((String) d[2]);
            e.setSiteId(siteByCode.get(siteCode).getId());
            e.setProductionLineId(line == null ? null : line.getId());
            e.setManufacturer((String) d[4]);
            e.setModel((String) d[5]);
            e.setSerialNumber("SN-" + (100000 + rnd.nextInt(899999)));
            e.setCommissioningDate(today.minusYears(2 + rnd.nextInt(10)).minusDays(rnd.nextInt(300)));
            e.setCriticality((String) d[6]);
            e.setMtbfHours((Double) d[7]);
            e.setStatus(d[0].equals("EQ-L2-PCK") ? "MAINTENANCE" : d[0].equals("EQ-STF-CMP1") ? "DOWN" : "OPERATIONAL");
            e.setRunningHours(8000 + rnd.nextInt(40000));
            e.setFailureCount(0);
            e.setExternalRef("SAP-PM-" + (10000000 + rnd.nextInt(8999999)));
            equipmentList.add(e);
        }
        for (int i = 1; i <= 8; i++) {
            Equipment e = new Equipment();
            String site = i <= 3 ? "CST" : i <= 5 ? "ALG" : i <= 7 ? "STF" : "ORN";
            e.setCode("EQ-FLT-" + String.format("%02d", i));
            e.setName("Chariot élévateur " + i + " — " + site);
            e.setCategory("Chariot élévateur");
            e.setSiteId(siteByCode.get(site).getId());
            e.setManufacturer(pick(List.of("Toyota", "Linde", "Still")));
            e.setModel(pick(List.of("8FBE20", "E20", "RX20")));
            e.setSerialNumber("FL-" + (10000 + rnd.nextInt(89999)));
            e.setCommissioningDate(today.minusYears(1 + rnd.nextInt(7)));
            e.setCriticality("MEDIUM");
            e.setMtbfHours(1800.0);
            e.setStatus("OPERATIONAL");
            e.setRunningHours(2000 + rnd.nextInt(12000));
            e.setFailureCount(0);
            equipmentList.add(e);
        }
        equipment.saveAll(equipmentList);
        List<MaintenanceWorkOrder> wos = new ArrayList<>();
        int ref = 0;
        for (Equipment e : equipmentList) {
            int interval = switch (e.getCriticality()) {
                case "CRITICAL" -> 45;
                case "HIGH" -> 60;
                default -> 90;
            };
            LocalDate last = null;
            for (LocalDate d = today.minusDays(365 - rnd.nextInt(interval)); d.isBefore(today); d = d.plusDays(interval + rnd.nextInt(10) - 5)) {
                MaintenanceWorkOrder w = workOrder(e, "PREVENTIVE", d, ++ref);
                w.setDowntimeHours(1.0 + rnd.nextInt(4));
                wos.add(w);
                last = d;
            }
            double failRate = e.getCode().equals("EQ-L2-PCK") ? 0.9 : e.getCode().equals("EQ-STF-CMP1") ? 0.5 : "CRITICAL".equals(e.getCriticality()) ? 0.15 : 0.25;
            int failures = 0;
            for (int m = 0; m < 12; m++) {
                if (!chance(failRate)) continue;
                LocalDate d = today.minusDays(m * 30L + rnd.nextInt(30));
                MaintenanceWorkOrder w = workOrder(e, "CORRECTIVE", d, ++ref);
                w.setPriority(pick(List.of("HIGH", "CRITICAL", "MEDIUM")));
                w.setDescription(pick(List.of("Rupture courroie d'entraînement", "Défaut capteur de position", "Fuite circuit pneumatique",
                        "Usure mâchoires de soudure", "Surchauffe moteur", "Défaut variateur de vitesse", "Bourrage récurrent")));
                w.setDowntimeHours(2.0 + rnd.nextInt(14));
                wos.add(w);
                failures++;
            }
            e.setFailureCount(failures);
            e.setLastMaintenanceDate(last);
            boolean overdue = e.getCode().equals("EQ-L4-OVN") || e.getCode().equals("EQ-FLT-04") || chance(0.08);
            e.setNextMaintenanceDate(last == null ? today.plusDays(10) : overdue ? today.minusDays(5 + rnd.nextInt(15)) : last.plusDays(interval));
        }
        // planned next preventive work orders
        for (Equipment e : equipmentList) {
            if (e.getNextMaintenanceDate() != null && e.getNextMaintenanceDate().isAfter(today)) {
                MaintenanceWorkOrder w = workOrder(e, "PREVENTIVE", e.getNextMaintenanceDate(), ++ref);
                w.setStatus("PLANNED");
                w.setCompletedDate(null);
                w.setDowntimeHours(null);
                wos.add(w);
            }
        }
        workOrders.saveAll(wos);
        equipment.saveAll(equipmentList);
    }

    private MaintenanceWorkOrder workOrder(Equipment e, String type, LocalDate d, int ref) {
        MaintenanceWorkOrder w = new MaintenanceWorkOrder();
        w.setReference("WO-" + d.getYear() + "-" + String.format("%05d", ref));
        w.setEquipmentId(e.getId());
        w.setType(type);
        w.setStatus("COMPLETED");
        w.setPriority("MEDIUM");
        w.setDescription("PREVENTIVE".equals(type) ? "Maintenance préventive planifiée (graissage, contrôle, réglages)" : "Intervention corrective");
        w.setPlannedDate(d);
        w.setCompletedDate(d);
        w.setCost(BigDecimal.valueOf(15000 + rnd.nextInt(180000)));
        w.setTechnician(pick(List.of("K. Benali", "S. Haddad", "M. Boudiaf", "R. Cherif", "A. Touati")));
        w.setSource("SAP PM");
        w.setExternalRef("SAP-PM-ORD-" + (4000000 + ref));
        return w;
    }

    // =============================================================== fleet
    private void seedFleet() {
        Object[][] fleet = {{"ALG", 7, "16"}, {"CST", 5, "25"}, {"ORN", 4, "31"}, {"STF", 3, "19"}};
        int n = 0;
        for (Object[] f : fleet) {
            Site s = siteByCode.get((String) f[0]);
            for (int i = 0; i < (Integer) f[1]; i++) {
                n++;
                String type = i == 0 ? "SEMI_TRAILER" : i % 3 == 0 ? "VAN" : "TRUCK";
                Vehicle v = new Vehicle();
                v.setPlateNumber(String.format("%05d", 10000 + rnd.nextInt(89999)) + "-" + (1 + (type.equals("VAN") ? 2 : 1)) + (18 + rnd.nextInt(7)) + "-" + f[2]);
                v.setCode("VH-" + String.format("%02d", n));
                v.setType(type);
                v.setBrand(type.equals("VAN") ? "Renault" : pick(List.of("Mercedes-Benz", "Volvo", "Iveco", "Renault Trucks")));
                v.setModel(type.equals("VAN") ? "Master" : type.equals("SEMI_TRAILER") ? "Actros 1845" : pick(List.of("Atego 1218", "FL 280", "Eurocargo 120")));
                v.setCapacityKg(switch (type) {
                    case "SEMI_TRAILER" -> 24000.0;
                    case "VAN" -> 1400.0;
                    default -> 7500.0;
                });
                v.setCapacityPallets(switch (type) {
                    case "SEMI_TRAILER" -> 33;
                    case "VAN" -> 3;
                    default -> 14;
                });
                v.setFuelType("DIESEL");
                v.setFuelConsumptionPer100km(switch (type) {
                    case "SEMI_TRAILER" -> 34.0;
                    case "VAN" -> 11.5;
                    default -> 24.0;
                });
                v.setCo2FactorKgPerLiter(2.68);
                v.setStatus(n == 6 ? "MAINTENANCE" : "AVAILABLE");
                v.setHomeSiteId(s.getId());
                v.setCurrentLatitude(jitter(s.getLatitude(), 0.01));
                v.setCurrentLongitude(jitter(s.getLongitude(), 0.01));
                v.setLastGpsAt(Instant.now().minus(Duration.ofMinutes(rnd.nextInt(30))));
                int odo = 40000 + rnd.nextInt(420000);
                v.setOdometerKm(odo);
                v.setNextServiceKm((odo / 20000 + 1) * 20000 + (n == 3 || n == 9 ? -19500 : 0));
                v.setLastHygieneCheck(today.minusDays(rnd.nextInt(10)));
                v.setRefrigerated(false);
                v.setExternalRef("GPS-" + (500 + n));
                vehicleList.add(v);
            }
        }
        vehicles.saveAll(vehicleList);
        int d = 0;
        for (Vehicle v : vehicleList) {
            for (int k = 0; k < (v.getType().equals("SEMI_TRAILER") ? 2 : 1); k++) {
                Driver dr = new Driver();
                boolean f = false;
                dr.setFirstName(pick(FIRST_NAMES_M));
                dr.setLastName(pick(LAST_NAMES));
                dr.setLicenseNumber("DZ-" + (1000000 + rnd.nextInt(8999999)));
                dr.setLicenseCategory(v.getType().equals("VAN") ? "B" : v.getType().equals("SEMI_TRAILER") ? "CE" : "C");
                dr.setLicenseExpiry(++d == 4 ? today.plusDays(20) : today.plusDays(200 + rnd.nextInt(1500)));
                dr.setPhone("+213 7" + (70000000 + rnd.nextInt(29999999)));
                dr.setStatus("AVAILABLE");
                dr.setSafetyScore(Math.round(jitter(86, 9) * 10) / 10.0);
                dr.setHomeSiteId(v.getHomeSiteId());
                dr.setHarshBrakingEvents(rnd.nextInt(14));
                dr.setSpeedingEvents(rnd.nextInt(9));
                driverList.add(dr);
            }
        }
        drivers.saveAll(driverList);
        List<FuelLog> logs = new ArrayList<>();
        for (Vehicle v : vehicleList) {
            Driver dr = driverList.stream().filter(x -> x.getHomeSiteId().equals(v.getHomeSiteId())).findFirst().orElse(driverList.get(0));
            int odo = v.getOdometerKm() - 52 * 900;
            for (int w = 52; w >= 1; w--) {
                double km = v.getType().equals("VAN") ? 600 + rnd.nextInt(500) : 900 + rnd.nextInt(900);
                odo += (int) km;
                FuelLog fl = new FuelLog();
                fl.setVehicleId(v.getId());
                fl.setDriverId(dr.getId());
                fl.setLogDate(today.minusWeeks(w).plusDays(rnd.nextInt(5)));
                fl.setLiters(Math.round(km * v.getFuelConsumptionPer100km() / 100 * jitter(1, 0.08) * 10) / 10.0);
                fl.setCost(BigDecimal.valueOf(Math.round(fl.getLiters() * 29.01)));
                fl.setOdometerKm(odo);
                fl.setStation(pick(List.of("Naftal Oued Smar", "Naftal Es-Sénia", "Naftal Constantine Zouaghi", "Naftal Sétif El Eulma", "Naftal Autoroute Est-Ouest")));
                logs.add(fl);
            }
        }
        fuelLogs.saveAll(logs);
    }

    // =============================================================== production batches
    private void seedBatches() {
        Map<String, List<String>> familySuppliers = Map.of("PASTA", List.of("SUP-001", "SUP-002"), "COUSCOUS", List.of("SUP-001", "SUP-002"),
                "BISCUITS", List.of("SUP-008", "SUP-009", "SUP-010"), "TISSUE", List.of("SUP-005", "SUP-006"), "DIAPERS", List.of("SUP-007", "SUP-005"));
        List<Batch> all = new ArrayList<>();
        LocalDate start = today.minusDays(560);
        for (ProductionLine line : lineByCode.values()) {
            List<ProductDef> lineProducts = PRODUCTS.stream().filter(p -> p.line().equals(line.getCode())).toList();
            int seq = 0;
            for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
                if (d.getDayOfWeek() == DayOfWeek.FRIDAY) continue; // weekly rest day
                for (ProductDef pd : lineProducts) {
                    // produce each product roughly in proportion to its demand
                    double perDay = pd.baseWeekly() / 6.0;
                    double batchSize = Math.max(250, pd.baseWeekly() * 1.6);
                    if (!chance(perDay / batchSize)) continue;
                    Batch b = new Batch();
                    b.setBatchNumber(line.getCode() + "-" + d.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd")) + "-" + String.format("%02d", ++seq % 100));
                    b.setProductId(productBySku.get(pd.sku()).getId());
                    b.setSiteId(line.getSiteId());
                    b.setProductionLineId(line.getId());
                    String supplierCode = pick(familySuppliers.get(pd.family()));
                    b.setSupplierId(supplierByCode.get(supplierCode).getId());
                    b.setProductionDate(d);
                    b.setExpiryDate(d.plusDays(pd.shelfLifeDays()));
                    b.setQuantityProduced((int) Math.round(batchSize * jitter(1, 0.15)));
                    boolean recent = ChronoUnit.DAYS.between(d, today) <= 1;
                    b.setStatus(recent ? "QUARANTINE" : "RELEASED");
                    b.setQualityStatus(recent ? "PENDING" : "APPROVED");
                    String film = chance(0.5) ? "; FILM-SUP003-" + (1000 + rnd.nextInt(8999)) : "; CART-SUP004-" + (1000 + rnd.nextInt(8999));
                    b.setRawMaterialLots(supplierCode + "-" + d.minusDays(3 + rnd.nextInt(10)).format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"))
                            + "-" + (100 + rnd.nextInt(899)) + film);
                    b.setExternalRef("SAP-CHARG-" + (2000000 + all.size()));
                    all.add(b);
                }
            }
        }
        batches.saveAll(all);
        for (Batch b : all) {
            String sku = PRODUCTS.stream().filter(p -> productBySku.get(p.sku()).getId().equals(b.getProductId())).findFirst().orElseThrow().sku();
            batchesBySku.computeIfAbsent(sku, k -> new ArrayList<>()).add(b);
        }
        batchesBySku.values().forEach(l -> l.sort(Comparator.comparing(Batch::getProductionDate)));
    }

    /** Most recent released batch of the product produced at least 3 days before the date (FEFO-like supply). */
    private Batch batchFor(String sku, LocalDate date) {
        List<Batch> list = batchesBySku.getOrDefault(sku, List.of());
        Batch best = null;
        for (Batch b : list) {
            if (b.getProductionDate().isAfter(date.minusDays(3))) break;
            best = b;
        }
        return best != null ? best : list.isEmpty() ? null : list.get(0);
    }

    // =============================================================== demand, orders & deliveries
    private double seasonality(ProductDef p, LocalDate d) {
        double f = 1.0;
        // Ramadan peaks (approximate Gregorian dates) for pasta, couscous and biscuits
        LocalDate[][] ramadan = {{LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 30)}, {LocalDate.of(2026, 2, 18), LocalDate.of(2026, 3, 19)},
                {LocalDate.of(2027, 2, 8), LocalDate.of(2027, 3, 9)}};
        for (LocalDate[] r : ramadan) {
            if (!d.isBefore(r[0].minusDays(21)) && !d.isAfter(r[1])) {
                f *= switch (p.family()) {
                    case "COUSCOUS" -> 1.75;
                    case "PASTA" -> 1.45;
                    case "BISCUITS" -> 1.3;
                    default -> 1.05;
                };
            }
        }
        int m = d.getMonthValue();
        if (p.family().equals("BISCUITS") && (m == 9 || m == 10)) f *= 1.3; // back to school
        if (p.family().equals("TISSUE") && (m >= 6 && m <= 8)) f *= 1.18;   // summer
        if (p.family().equals("PASTA") && (m >= 6 && m <= 8)) f *= 0.85;
        if (p.family().equals("COUSCOUS") && (m == 12 || m == 1)) f *= 1.15;
        return f;
    }

    private void seedOrdersAndDeliveries() {
        LocalDate start = today.minusDays(546).with(DayOfWeek.SATURDAY);
        Map<String, List<Customer>> byHub = customerList.stream().collect(Collectors.groupingBy(c -> customerHub.get(c.getId())));
        Map<String, Warehouse> hubWarehouse = Map.of("ALG", whByCode.get("WH-ALG-DC"), "ORN", whByCode.get("WH-ORN"),
                "CST", whByCode.get("WH-CST-PF"), "STF", whByCode.get("WH-STF"));
        Map<String, Double> hubShare = Map.of("ALG", 0.40, "ORN", 0.22, "CST", 0.24, "STF", 0.14);
        List<SalesOrder> orderBatch = new ArrayList<>();
        List<SalesOrderLine> lineBatch = new ArrayList<>();
        List<Delivery> deliveryBatch = new ArrayList<>();
        int orderSeq = 0;
        Map<String, List<Vehicle>> fleetByHub = new HashMap<>();
        for (Vehicle v : vehicleList) {
            String hub = siteByCode.entrySet().stream().filter(e -> e.getValue().getId().equals(v.getHomeSiteId())).findFirst().orElseThrow().getKey();
            fleetByHub.computeIfAbsent(hub, k -> new ArrayList<>()).add(v);
        }
        Map<UUID, List<Driver>> driversBySite = driverList.stream().collect(Collectors.groupingBy(Driver::getHomeSiteId));
        for (LocalDate week = start; week.isBefore(today.minusDays(1)); week = week.plusWeeks(1)) {
            double trend = 1 + 0.08 * ChronoUnit.DAYS.between(start, week) / 365.0;
            for (String hub : List.of("ALG", "ORN", "CST", "STF")) {
                List<Customer> custs = byHub.get(hub);
                int nOrders = (int) Math.round(38 * hubShare.get(hub) * jitter(1, 0.15));
                List<SalesOrder> weekOrders = new ArrayList<>();
                for (int i = 0; i < nOrders; i++) {
                    LocalDate od = week.plusDays(rnd.nextInt(6));
                    if (!od.isBefore(today)) od = today.minusDays(1);
                    SalesOrder o = new SalesOrder();
                    o.setId(UUID.randomUUID());
                    o.setOrderNumber("SO-" + od.getYear() + "-" + String.format("%05d", ++orderSeq));
                    o.setCustomerId(pick(custs).getId());
                    o.setWarehouseId(hubWarehouse.get(hub).getId());
                    o.setOrderDate(od);
                    o.setRequestedDate(od.plusDays(1 + rnd.nextInt(3)));
                    o.setPriority(chance(0.08) ? "URGENT" : chance(0.2) ? "HIGH" : "NORMAL");
                    o.setTotalAmount(BigDecimal.ZERO);
                    o.setTotalWeightKg(0.0);
                    weekOrders.add(o);
                }
                for (ProductDef p : PRODUCTS) {
                    double weekly = p.baseWeekly() * hubShare.get(hub) * seasonality(p, week) * trend * jitter(1, 0.12);
                    int remaining = (int) Math.round(weekly);
                    int parts = Math.max(1, Math.min(weekOrders.size(), remaining / 15));
                    List<SalesOrder> targets = new ArrayList<>(weekOrders);
                    Collections.shuffle(targets, rnd);
                    for (int k = 0; k < parts && remaining > 0; k++) {
                        int q = k == parts - 1 ? remaining : Math.max(1, remaining / (parts - k) + rnd.nextInt(5) - 2);
                        q = Math.min(q, remaining);
                        remaining -= q;
                        SalesOrder o = targets.get(k);
                        SalesOrderLine l = new SalesOrderLine();
                        l.setOrderId(o.getId());
                        l.setProductId(productBySku.get(p.sku()).getId());
                        Batch b = batchFor(p.sku(), o.getOrderDate());
                        l.setBatchId(b == null ? null : b.getId());
                        l.setQuantity(q);
                        l.setUnitPrice(BigDecimal.valueOf(p.price()));
                        l.setLineTotal(BigDecimal.valueOf((long) p.price() * q));
                        lineBatch.add(l);
                        o.setTotalAmount(o.getTotalAmount().add(l.getLineTotal()));
                        o.setTotalWeightKg(Math.round((o.getTotalWeightKg() + q * p.weightKg()) * 10) / 10.0);
                    }
                }
                for (SalesOrder o : weekOrders) {
                    if (o.getTotalAmount().signum() == 0) continue;
                    long age = ChronoUnit.DAYS.between(o.getOrderDate(), today);
                    boolean cancelled = chance(0.015);
                    o.setStatus(cancelled ? "CANCELLED" : age > 4 ? "DELIVERED" : age > 2 ? "SHIPPED" : "CONFIRMED");
                    orderBatch.add(o);
                    if (cancelled) continue;
                    Customer c = customerList.stream().filter(x -> x.getId().equals(o.getCustomerId())).findFirst().orElseThrow();
                    Warehouse w = hubWarehouse.get(hub);
                    Vehicle v = pick(fleetByHub.get(hub));
                    List<Driver> ds = driversBySite.get(v.getHomeSiteId());
                    Delivery d = new Delivery();
                    d.setDeliveryNumber("DLV-" + o.getOrderDate().getYear() + "-" + o.getOrderNumber().substring(8));
                    d.setOrderId(o.getId());
                    d.setCustomerId(c.getId());
                    d.setWarehouseId(w.getId());
                    d.setVehicleId(v.getId());
                    d.setDriverId(ds == null ? null : pick(ds).getId());
                    d.setPlannedDate(o.getRequestedDate());
                    d.setTimeWindowStart(pick(List.of("08:00", "09:00", "10:00", "13:00")));
                    d.setTimeWindowEnd(String.format("%02d:00", Integer.parseInt(d.getTimeWindowStart().substring(0, 2)) + 4));
                    double dist = HeuristicRouteSolver.haversineKm(w.getLatitude(), w.getLongitude(), c.getLatitude(), c.getLongitude()) * 1.3 + 8;
                    d.setDistanceKm(EmissionCalculator.round(dist));
                    if ("DELIVERED".equals(o.getStatus())) {
                        boolean late = chance(hub.equals("ORN") ? 0.16 : 0.09);
                        int delay = late ? 20 + rnd.nextInt(180) : 0;
                        LocalTime end = LocalTime.parse(d.getTimeWindowEnd());
                        LocalDateTime deliveredAt = d.getPlannedDate().atTime(late ? end : end.minusMinutes(30 + rnd.nextInt(150))).plusMinutes(delay);
                        d.setDepartedAt(d.getPlannedDate().atTime(6, 30).atZone(ZONE).toInstant());
                        d.setDeliveredAt(deliveredAt.atZone(ZONE).toInstant());
                        d.setDelayMinutes(delay);
                        d.setStatus("DELIVERED");
                        double liters = EmissionCalculator.fuelLiters(dist, v.getFuelConsumptionPer100km());
                        d.setFuelLiters(liters);
                        d.setCo2Kg(EmissionCalculator.co2Kg(liters, v.getCo2FactorKgPerLiter(), v.getFuelType()));
                        d.setProofNote("Bon de livraison signé par le client");
                    } else {
                        d.setStatus("PLANNED");
                        d.setVehicleId(null);
                        d.setDriverId(null);
                    }
                    deliveryBatch.add(d);
                }
            }
            if (orderBatch.size() > 400) {
                orders.saveAll(orderBatch);
                orderLines.saveAll(lineBatch);
                deliveries.saveAll(deliveryBatch);
                orderBatch.clear();
                lineBatch.clear();
                deliveryBatch.clear();
            }
        }
        orders.saveAll(orderBatch);
        orderLines.saveAll(lineBatch);
        deliveries.saveAll(deliveryBatch);
    }

    // =============================================================== stock & movements
    private void seedStock() {
        Map<String, Double> split = Map.of("WH-ALG-DC", 0.34, "WH-ORN", 0.2, "WH-CST-PF", 0.24, "WH-STF", 0.22);
        Set<String> lowStock = Set.of("ATL-CS-302", "ATL-BS-401", "ATL-BB-702");
        List<StockLevel> levels = new ArrayList<>();
        List<StockMovement> mvts = new ArrayList<>();
        for (ProductDef p : PRODUCTS) {
            Product prod = productBySku.get(p.sku());
            List<Batch> list = batchesBySku.getOrDefault(p.sku(), List.of());
            List<Batch> recent = list.stream().filter(b -> "RELEASED".equals(b.getStatus())).skip(Math.max(0, list.size() - 6)).toList();
            double factor = lowStock.contains(p.sku()) ? 0.25 : 1.0;
            int cover = (int) Math.round(p.baseWeekly() * 2.3 * factor);
            for (int i = 0; i < recent.size(); i++) {
                Batch b = recent.get(i);
                String productionWh = Set.of("L5", "L6", "L7").contains(p.line()) ? "WH-STF" : "WH-CST-PF";
                MovementAdd(mvts, "PRODUCTION", prod.getId(), b.getId(), null, whByCode.get(productionWh).getId(), b.getQuantityProduced(), at(b.getProductionDate(), 18, 0), "Réception production");
                for (Map.Entry<String, Double> e : split.entrySet()) {
                    int qty = (int) Math.round(cover * e.getValue() * (i + 1) / (double) (recent.size() * (recent.size() + 1) / 2));
                    if (qty <= 0) continue;
                    StockLevel l = new StockLevel();
                    l.setWarehouseId(whByCode.get(e.getKey()).getId());
                    l.setProductId(prod.getId());
                    l.setBatchId(b.getId());
                    l.setQuantity(qty);
                    l.setReservedQuantity(0);
                    l.setLocationCode(pick(List.of("A", "B", "C", "D")) + "-" + (1 + rnd.nextInt(30)) + "-" + (1 + rnd.nextInt(5)));
                    levels.add(l);
                    if (!e.getKey().equals(productionWh)) {
                        MovementAdd(mvts, "TRANSFER", prod.getId(), b.getId(), whByCode.get(productionWh).getId(), l.getWarehouseId(), qty + rnd.nextInt(20),
                                at(b.getProductionDate().plusDays(1 + rnd.nextInt(2)), 9, 0), "Transfert inter-sites");
                    }
                }
            }
        }
        stockLevels.saveAll(levels);
        // shipments of the last 45 days (one movement per order line)
        LocalDate since = today.minusDays(45);
        Map<UUID, SalesOrder> recentOrders = orders.findAll((r, q, cb) -> cb.and(cb.greaterThanOrEqualTo(r.get("orderDate"), since),
                r.get("status").in("SHIPPED", "DELIVERED"))).stream().collect(Collectors.toMap(SalesOrder::getId, o -> o));
        for (SalesOrderLine l : orderLines.findAll((r, q, cb) -> r.get("orderId").in(recentOrders.keySet()))) {
            SalesOrder o = recentOrders.get(l.getOrderId());
            MovementAdd(mvts, "SHIPMENT", l.getProductId(), l.getBatchId(), o.getWarehouseId(), null, l.getQuantity(), at(o.getRequestedDate(), 7, 0), null);
            mvts.get(mvts.size() - 1).setReferenceType("SalesOrder");
            mvts.get(mvts.size() - 1).setReferenceId(o.getId());
        }
        movements.saveAll(mvts);
        // today's planned deliveries for route optimization, reserved on available stock
        List<Delivery> planned = new ArrayList<>();
        List<SalesOrder> newOrders = new ArrayList<>();
        List<SalesOrderLine> newLines = new ArrayList<>();
        Map<String, Integer> counts = Map.of("WH-ALG-DC", 22, "WH-CST-PF", 14, "WH-ORN", 10);
        int seq = 90000;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Warehouse w = whByCode.get(e.getKey());
            String hub = e.getKey().equals("WH-ALG-DC") ? "ALG" : e.getKey().equals("WH-ORN") ? "ORN" : "CST";
            List<Customer> custs = new ArrayList<>(customerList.stream().filter(c -> hub.equals(customerHub.get(c.getId()))).toList());
            Collections.shuffle(custs, rnd);
            List<StockLevel> available = levels.stream().filter(l -> l.getWarehouseId().equals(w.getId()) && l.getQuantity() > 10).toList();
            for (int i = 0; i < e.getValue() && i < custs.size() * 2; i++) {
                Customer c = custs.get(i % custs.size());
                SalesOrder o = new SalesOrder();
                o.setId(UUID.randomUUID());
                o.setOrderNumber("SO-" + today.getYear() + "-" + (++seq));
                o.setCustomerId(c.getId());
                o.setWarehouseId(w.getId());
                o.setOrderDate(today.minusDays(1));
                o.setRequestedDate(today);
                o.setStatus("CONFIRMED");
                o.setPriority(chance(0.15) ? "URGENT" : chance(0.25) ? "HIGH" : "NORMAL");
                o.setTotalAmount(BigDecimal.ZERO);
                o.setTotalWeightKg(0.0);
                for (int k = 0; k < 2 + rnd.nextInt(3); k++) {
                    StockLevel lvl = pick(available);
                    int free = lvl.getQuantity() - lvl.getReservedQuantity();
                    if (free < 4) continue;
                    int q = Math.min(free / 2, 8 + rnd.nextInt(30));
                    lvl.setReservedQuantity(lvl.getReservedQuantity() + q);
                    ProductDef p = defBySku.values().stream().filter(x -> productBySku.get(x.sku()).getId().equals(lvl.getProductId())).findFirst().orElseThrow();
                    SalesOrderLine l = new SalesOrderLine();
                    l.setOrderId(o.getId());
                    l.setProductId(lvl.getProductId());
                    l.setBatchId(lvl.getBatchId());
                    l.setQuantity(q);
                    l.setUnitPrice(BigDecimal.valueOf(p.price()));
                    l.setLineTotal(BigDecimal.valueOf((long) p.price() * q));
                    newLines.add(l);
                    o.setTotalAmount(o.getTotalAmount().add(l.getLineTotal()));
                    o.setTotalWeightKg(Math.round((o.getTotalWeightKg() + q * p.weightKg()) * 10) / 10.0);
                }
                if (o.getTotalAmount().signum() == 0) continue;
                newOrders.add(o);
                Delivery d = new Delivery();
                d.setDeliveryNumber("DLV-" + today.getYear() + "-" + seq);
                d.setOrderId(o.getId());
                d.setCustomerId(c.getId());
                d.setWarehouseId(w.getId());
                d.setPlannedDate(today);
                String ws = pick(List.of("08:00", "08:00", "09:00", "10:00", "13:00"));
                d.setTimeWindowStart(ws);
                d.setTimeWindowEnd(String.format("%02d:00", Integer.parseInt(ws.substring(0, 2)) + 5));
                d.setStatus("PLANNED");
                planned.add(d);
            }
        }
        orders.saveAll(newOrders);
        orderLines.saveAll(newLines);
        stockLevels.saveAll(levels);
        deliveries.saveAll(planned);
    }

    @SuppressWarnings("checkstyle:MethodName")
    private void MovementAdd(List<StockMovement> list, String type, UUID productId, UUID batchId, UUID from, UUID to, int qty, Instant when, String notes) {
        StockMovement m = new StockMovement();
        m.setType(type);
        m.setProductId(productId);
        m.setBatchId(batchId);
        m.setFromWarehouseId(from);
        m.setToWarehouseId(to);
        m.setQuantity(qty);
        m.setOccurredAt(when);
        m.setNotes(notes);
        list.add(m);
    }

    // =============================================================== route plans (computed with the real solver)
    private void seedRoutePlans() {
        Warehouse depot = whByCode.get("WH-ALG-DC");
        List<Vehicle> fleet = vehicleList.stream().filter(v -> v.getHomeSiteId().equals(depot.getSiteId()) && !"MAINTENANCE".equals(v.getStatus())).toList();
        Map<UUID, Customer> cust = customerList.stream().collect(Collectors.toMap(Customer::getId, c -> c));
        int n = 0;
        for (int back = 28; back >= 4; back -= 6) {
            LocalDate date = today.minusDays(back);
            List<Delivery> ds = deliveries.findAll((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), depot.getId()),
                    cb.between(r.get("plannedDate"), date, date.plusDays(3))));
            if (ds.size() < 3) continue;
            Map<UUID, SalesOrder> ord = orders.findAllById(ds.stream().map(Delivery::getOrderId).toList()).stream().collect(Collectors.toMap(SalesOrder::getId, o -> o));
            List<RoutingModels.Stop> stops = ds.stream().map(d -> {
                Customer c = cust.get(d.getCustomerId());
                SalesOrder o = ord.get(d.getOrderId());
                return new RoutingModels.Stop(d.getId().toString(), c.getName() + " (" + c.getCity() + ")", c.getLatitude(), c.getLongitude(),
                        o == null ? 400 : o.getTotalWeightKg(), 1, null, null, 20);
            }).toList();
            List<RoutingModels.VehicleSpec> specs = fleet.stream().map(v -> new RoutingModels.VehicleSpec(v.getId().toString(), v.getPlateNumber(),
                    v.getCapacityKg(), v.getFuelConsumptionPer100km(), 2.68)).toList();
            RoutingModels.Problem p = new RoutingModels.Problem(new RoutingModels.Location(depot.getLatitude(), depot.getLongitude()), stops, specs, 55, 1.3, 11 * 60);
            RoutingModels.Solution s = new HeuristicRouteSolver().solve(p);
            RoutePlan plan = new RoutePlan();
            plan.setCode("RP-" + date.getYear() + "-" + String.format("%04d", ++n));
            plan.setPlanDate(date);
            plan.setDepotWarehouseId(depot.getId());
            plan.setStatus("COMPLETED");
            plan.setAlgorithm(s.engine());
            plan.setOriginalDistanceKm(s.originalDistanceKm());
            plan.setOptimizedDistanceKm(s.optimizedDistanceKm());
            plan.setEstimatedDurationMin(s.estimatedDurationMinutes());
            plan.setEstimatedFuelLiters(s.estimatedFuelLiters());
            plan.setEstimatedCo2Kg(s.estimatedCo2Kg());
            plan.setVehiclesUsed(s.routes().size());
            plan.setStopsCount(stops.size());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = mapper.convertValue(Map.of("planDate", date.toString(), "solution", s,
                    "depot", Map.of("id", depot.getId(), "name", depot.getName(), "latitude", depot.getLatitude(), "longitude", depot.getLongitude())), Map.class);
            plan.setResult(result);
            routePlans.save(plan);
        }
    }

    // =============================================================== inspections, non-conformities & CAPA
    private Object answerFor(Map<String, Object> q, boolean pass) {
        String type = String.valueOf(q.get("type"));
        return switch (type) {
            case "YES_NO" -> {
                String expected = String.valueOf(q.getOrDefault("expected", "YES"));
                yield pass ? expected : (expected.equals("YES") ? "NO" : "YES");
            }
            case "PASS_FAIL" -> pass ? "PASS" : "FAIL";
            case "NUMERIC" -> {
                Double min = q.get("min") instanceof Number n ? n.doubleValue() : null;
                Double max = q.get("max") instanceof Number n ? n.doubleValue() : null;
                double lo = min == null ? 0 : min, hi = max == null ? lo + 10 : max;
                if (pass) yield Math.round((lo + (hi - lo) * (0.2 + rnd.nextDouble() * 0.6)) * 10) / 10.0;
                yield Math.round((hi + Math.max(0.5, (hi - lo) * 0.15)) * 10) / 10.0;
            }
            case "MULTI_SELECT" -> pass ? List.of("None") : List.of(pick(List.of("Under-weight", "Poor sealing")));
            case "SIGNATURE" -> "signed";
            case "TEXT" -> pass ? "RAS" : "Écart constaté, voir photo";
            default -> null;
        };
    }

    private record NcSpec(String type, String category, String severity, String title, String description, String rootCause, List<String> whys,
                          String action) {
    }

    private void seedInspectionsAndNcs() {
        String[] inspectors = {"Karim Boumediene", "Lynda Saadi", "Meriem Brahimi", "Nabil Kaci", "Nadia Belkacem", "Farid Zerrouki"};
        List<Inspection> list = new ArrayList<>();
        List<NonConformity> ncList = new ArrayList<>();
        int insSeq = 0;
        Supplier plast = supplierByCode.get("SUP-003"), cacao = supplierByCode.get("SUP-008");
        List<Supplier> rawSuppliers = new ArrayList<>(supplierByCode.values());
        for (LocalDate d = today.minusDays(365); d.isBefore(today); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.FRIDAY) continue;
            for (String siteCode : List.of("CST", "STF")) {
                Site s = siteByCode.get(siteCode);
                List<ProductionLine> siteLines = lineByCode.values().stream().filter(l -> l.getSiteId().equals(s.getId())).toList();
                if (chance(0.5)) list.add(inspection(++insSeq, "QC-INCOMING", s, d, pick(rawSuppliers), null, null, null, inspectors[0]));
                if (chance(0.65)) {
                    ProductionLine l = pick(siteLines);
                    list.add(inspection(++insSeq, "QC-INPROCESS", s, d, null, l, null, null, inspectors[3]));
                }
                if (chance(0.3)) list.add(inspection(++insSeq, "QC-FINISHED", s, d, null, pick(siteLines), null, null, inspectors[2]));
                if (chance(0.3)) list.add(inspection(++insSeq, "HY-PERSONNEL", s, d, null, null, null, null, inspectors[1]));
            }
            if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
                for (Warehouse w : whByCode.values()) {
                    Site s = sites.findById(w.getSiteId()).orElseThrow();
                    list.add(inspection(++insSeq, "HY-WAREHOUSE", s, d, null, null, w, null, inspectors[1]));
                }
            }
            if (chance(0.4)) {
                Vehicle v = pick(vehicleList);
                list.add(inspection(++insSeq, "HY-VEHICLE", sites.findById(v.getHomeSiteId()).orElseThrow(), d, null, null, null, v, inspectors[1]));
            }
            if (d.getDayOfMonth() == 10) {
                for (Site s : siteByCode.values()) {
                    list.add(inspection(++insSeq, "SF-WALK", s, d.plusDays(rnd.nextInt(5)), null, null, null, null, inspectors[4]));
                    list.add(inspection(++insSeq, "EN-COMPLIANCE", s, d.plusDays(rnd.nextInt(5)), null, null, null, null, inspectors[5]));
                    if (d.getMonthValue() % 3 == 0) list.add(inspection(++insSeq, "SF-FIRE", s, d.plusDays(2), null, null, null, null, inspectors[4]));
                }
            }
        }
        // upcoming & overdue scheduled inspections
        for (int i = 0; i < 18; i++) {
            Site s = pick(new ArrayList<>(siteByCode.values()));
            String code = pick(List.of("HY-WAREHOUSE", "SF-WALK", "HY-PERSONNEL", "HY-PERSONNEL", "EN-COMPLIANCE", "SF-FIRE"));
            LocalDate d = today.plusDays(i < 3 ? -(1 + rnd.nextInt(4)) : rnd.nextInt(14));
            Inspection ins = inspection(++insSeq, code, s, d, null, null, null, null, inspectors[code.startsWith("HY") ? 1 : code.startsWith("SF") ? 4 : 0]);
            ins.setStatus("SCHEDULED");
            ins.setAnswers(null);
            ins.setScore(null);
            ins.setResult(null);
            ins.setCompletedAt(null);
            ins.setSignature(null);
            if (code.startsWith("HY")) ins.setInspectorUserId(user("hygiene").getId());
            if (code.startsWith("SF")) ins.setInspectorUserId(user("safety").getId());
            list.add(ins);
        }
        inspections.saveAll(list);

        // NCs raised by failed inspection criteria (same rule as the live inspection engine)
        int ncSeq = 0;
        for (Inspection ins : list) {
            if (!"COMPLETED".equals(ins.getStatus()) || ins.getAnswers() == null) continue;
            InspectionTemplate t = templateByCode.values().stream().filter(x -> x.getId().equals(ins.getTemplateId())).findFirst().orElseThrow();
            ChecklistScorer.Evaluation ev = ChecklistScorer.evaluate(t.getQuestions(), ins.getAnswers(), t.getPassingScore());
            int raised = 0;
            for (ChecklistScorer.Failure f : ev.failures()) {
                if (!f.createNc()) continue;
                NonConformity nc = baseNc(++ncSeq, ins.getCompletedAt());
                nc.setTitle("Critère non conforme : " + (f.text().length() > 180 ? f.text().substring(0, 180) : f.text()));
                nc.setDescription("Relevé lors de l'inspection " + ins.getReference() + " (" + ins.getTitle() + "). Valeur observée : " + f.value());
                nc.setType(switch (ins.getDomain()) {
                    case "HYGIENE" -> "HYGIENE";
                    case "SAFETY" -> "SAFETY";
                    case "ENVIRONMENT" -> "ENVIRONMENT";
                    default -> "INCOMING".equals(ins.getInspectionType()) ? "SUPPLIER" : "PRODUCT";
                });
                nc.setCategory("INSPECTION_FINDING");
                nc.setSeverity(f.critical() ? "MAJOR" : "MINOR");
                nc.setSource("INSPECTION");
                nc.setInspectionId(ins.getId());
                nc.setSiteId(ins.getSiteId());
                nc.setProductionLineId(ins.getProductionLineId());
                nc.setBatchId(ins.getBatchId());
                nc.setProductId(ins.getProductId());
                nc.setSupplierId(ins.getSupplierId());
                nc.setReportedByName(ins.getInspectorName());
                ncList.add(nc);
                raised++;
            }
            ins.setGeneratedNcCount(raised);
        }
        inspections.saveAll(list);

        // Recurrent and notable non-conformities (customer complaints, process, supplier…)
        ProductionLine l2 = lineByCode.get("L2");
        List<NcSpec> specs = List.of(
                new NcSpec("PROCESS", "SEALING_DEFECT", "MAJOR", "Soudure défectueuse des sachets — ligne pâtes courtes",
                        "Sachets mal soudés détectés au contrôle d'étanchéité ; risque d'humidité et d'infestation.",
                        "Usure des mâchoires de soudure de l'ensacheuse L2 et température de soudure instable (régulateur défaillant).",
                        List.of("Pourquoi des sachets fuient ? Soudure incomplète", "Pourquoi ? Température des mâchoires trop basse",
                                "Pourquoi ? Régulateur de température instable", "Pourquoi ? Sonde PT100 dégradée", "Pourquoi ? Non incluse dans le plan préventif"),
                        "Isolement des palettes produites depuis le dernier contrôle conforme et tri à 100 %."),
                new NcSpec("SUPPLIER", "PACKAGING_FILM", "MAJOR", "Épaisseur film d'emballage hors tolérance — PlastEmball",
                        "Mesures d'épaisseur 38 µm pour une spécification de 45 ± 3 µm sur 3 bobines.",
                        "Changement de matière première non notifié par le fournisseur.",
                        List.of("Pourquoi le film est trop fin ? Extrusion modifiée", "Pourquoi ? Nouvelle résine utilisée", "Pourquoi ? Rupture fournisseur de résine",
                                "Pourquoi non notifié ? Absence de procédure de gestion des changements chez le fournisseur"),
                        "Bobines bloquées et retournées ; utilisation du stock de sécurité."),
                new NcSpec("CUSTOMER_COMPLAINT", "UNDER_WEIGHT", "MINOR", "Réclamation client : sachets sous-pesés",
                        "Le client signale des sachets de 480 g pour un poids nominal de 500 g.", "Dérive de la balance associative non détectée entre deux étalonnages.",
                        List.of("Pourquoi sous-pesé ? Dérive balance", "Pourquoi ? Étalonnage trimestriel insuffisant"), "Vérification à 100 % des cartons en stock du lot concerné."),
                new NcSpec("PRODUCT", "MOISTURE", "MINOR", "Humidité produit fini hors spécification", "Humidité mesurée 13,1 % pour une limite de 12,5 %.",
                        "Cycle de séchage raccourci lors d'un changement de format.", List.of("Pourquoi humidité élevée ? Séchage incomplet", "Pourquoi ? Recette séchoir non mise à jour"),
                        "Lot mis en quarantaine, re-séchage d'un échantillon pour validation."),
                new NcSpec("HYGIENE", "PEST", "MAJOR", "Traces de rongeurs — quai de chargement", "Déjections observées près du quai 3 lors de l'inspection hebdomadaire.",
                        "Joint de porte de quai endommagé permettant l'intrusion.", List.of("Pourquoi présence ? Point d'entrée", "Pourquoi ? Joint de quai détérioré non signalé"),
                        "Nettoyage-désinfection, pose de pièges supplémentaires, intervention prestataire 3D."),
                new NcSpec("ENVIRONMENT", "WASTEWATER", "MINOR", "pH des eaux usées hors limites", "pH mesuré à 9,2 en sortie de station NEP (limite 8,5).",
                        "Rinçage insuffisant après cycle de soude.", List.of("Pourquoi pH élevé ? Soude résiduelle", "Pourquoi ? Temps de rinçage réduit"),
                        "Neutralisation en bassin tampon, contrôle renforcé."));
        for (int month = 11; month >= 0; month--) {
            for (int k = 0; k < 2 + rnd.nextInt(2); k++) ncList.add(fromSpec(++ncSeq, specs.get(0), today.minusDays(month * 30L + rnd.nextInt(28)), l2));
            if (chance(0.8)) ncList.add(fromSpec(++ncSeq, specs.get(1), today.minusDays(month * 30L + rnd.nextInt(28)), null));
            for (int k = 0; k < 2; k++) ncList.add(fromSpec(++ncSeq, specs.get(2 + rnd.nextInt(4)), today.minusDays(month * 30L + rnd.nextInt(28)),
                    pick(new ArrayList<>(lineByCode.values()))));
        }
        // anomaly: spike of sealing defects on L2 during the last week
        for (int k = 0; k < 4; k++) ncList.add(fromSpec(++ncSeq, specs.get(0), today.minusDays(rnd.nextInt(6)), l2));
        // a critical customer complaint on a recent batch → batch blocked
        Batch suspect = batchFor("ATL-PA-201", today.minusDays(12));
        NonConformity critical = baseNc(++ncSeq, at(today.minusDays(3), 10, 15));
        critical.setTitle("Corps étranger métallique signalé par un client — Coquillettes 500 g");
        critical.setDescription("Le client Hypermarché Méditerranée (Alger) signale un fragment métallique de 4 mm dans un sachet. Échantillon récupéré pour analyse.");
        critical.setType("CUSTOMER_COMPLAINT");
        critical.setCategory("FOREIGN_BODY");
        critical.setSeverity("CRITICAL");
        critical.setSource("CUSTOMER");
        critical.setSiteId(siteByCode.get("CST").getId());
        critical.setProductionLineId(l2.getId());
        critical.setProductId(productBySku.get("ATL-PA-201").getId());
        critical.setBatchId(suspect.getId());
        critical.setQuantityAffected(1);
        critical.setImmediateAction("Lot bloqué dans tous les entrepôts, vérification du détecteur de métaux L2, analyse du fragment.");
        critical.setReportedByName("Service clients");
        ncList.add(critical);
        suspect.setStatus("BLOCKED");
        batches.save(suspect);

        Map<String, String> owners = Map.of("PRODUCT", "quality", "PROCESS", "quality", "SUPPLIER", "quality", "CUSTOMER_COMPLAINT", "quality",
                "HYGIENE", "hygiene", "SAFETY", "safety", "ENVIRONMENT", "environment");
        int capaSeq = 0;
        Map<Integer, Integer> perYear = new HashMap<>();
        ncList.sort(Comparator.comparing(NonConformity::getDetectedAt));
        for (NonConformity nc : ncList) {
            int y = nc.getDetectedAt().atZone(ZONE).getYear();
            nc.setReference("NC-" + y + "-" + String.format("%04d", perYear.merge(y, 1, Integer::sum)));
        }
        ncs.saveAll(ncList);
        List<CapaAction> capaList = new ArrayList<>();
        for (NonConformity nc : ncList) {
            long age = ChronoUnit.DAYS.between(nc.getDetectedAt().atZone(ZONE).toLocalDate(), today);
            String ownerKey = owners.getOrDefault(nc.getType(), "quality");
            User owner = user(ownerKey);
            nc.setDueDate(nc.getDetectedAt().atZone(ZONE).toLocalDate().plusDays("CRITICAL".equals(nc.getSeverity()) ? 7 : "MAJOR".equals(nc.getSeverity()) ? 15 : 30));
            String target;
            if (nc == critical) target = "ROOT_CAUSE_ANALYSIS";
            else if (age > 120) target = chance(0.97) ? "CLOSED" : "PENDING_VERIFICATION";
            else if (age > 50) target = chance(0.88) ? "CLOSED" : chance(0.5) ? "ACTION_PLAN" : "PENDING_VERIFICATION";
            else if (age > 20) target = pick(List.of("CLOSED", "CLOSED", "PENDING_VERIFICATION", "ACTION_PLAN", "ACTION_PLAN"));
            else if (age > 7) target = pick(List.of("ACTION_PLAN", "ROOT_CAUSE_ANALYSIS", "ASSIGNED", "PENDING_VERIFICATION"));
            else target = pick(List.of("REPORTED", "REPORTED", "ASSIGNED", "ROOT_CAUSE_ANALYSIS"));
            if (chance(0.02) && age > 20) target = "CANCELLED";
            Instant t = nc.getDetectedAt();
            event("NonConformity", nc.getId(), null, "REPORTED", "create", nc.getReportedByName(), t, null);
            String[] path = {"ASSIGNED", "ROOT_CAUSE_ANALYSIS", "ACTION_PLAN", "PENDING_VERIFICATION", "CLOSED"};
            String[] codes = {"qualify", "start_analysis", "submit_rca", "submit_verification", "close"};
            String current = "REPORTED";
            if ("CANCELLED".equals(target)) {
                event("NonConformity", nc.getId(), "REPORTED", "CANCELLED", "cancel", "Amina Khelifi", t.plus(Duration.ofHours(20)), "Doublon d'une NC existante");
                current = "CANCELLED";
            } else {
                for (int i = 0; i < path.length; i++) {
                    if (current.equals(target)) break;
                    t = t.plus(Duration.ofHours(10 + rnd.nextInt(60)));
                    if (t.isAfter(Instant.now())) t = Instant.now().minus(Duration.ofMinutes(30 + rnd.nextInt(300)));
                    String actor = i == 0 || i == 4 ? "Amina Khelifi" : owner.fullName();
                    event("NonConformity", nc.getId(), current, path[i], codes[i], actor, t,
                            i == 0 ? "Gravité confirmée, responsable désigné" : i == 4 ? "Actions vérifiées efficaces, clôture" : null);
                    current = path[i];
                }
            }
            nc.setStatus(current);
            if (!"REPORTED".equals(current) && !"CANCELLED".equals(current)) {
                nc.setAssigneeUserId(owner.getId());
                nc.setAssigneeName(owner.fullName());
            }
            if (Set.of("ACTION_PLAN", "PENDING_VERIFICATION", "CLOSED").contains(current)) {
                if (nc.getRootCause() == null) {
                    nc.setRootCause(pick(List.of("Formation insuffisante de l'opérateur au nouveau standard", "Réglage machine non conforme après maintenance",
                            "Spécification fournisseur incomplète", "Procédure de nettoyage non respectée en fin de poste", "Contrôle de démarrage non réalisé")));
                    nc.setRootCauseMethod("5WHY");
                } else {
                    nc.setRootCauseMethod("5WHY");
                }
                int nCapa = "MINOR".equals(nc.getSeverity()) ? 1 : 2;
                for (int k = 0; k < nCapa; k++) {
                    CapaAction c = new CapaAction();
                    c.setReference("CAPA-" + nc.getDetectedAt().atZone(ZONE).getYear() + "-" + String.format("%04d", ++capaSeq));
                    c.setType(k == 0 ? "CORRECTIVE" : "PREVENTIVE");
                    c.setTitle(k == 0 ? pick(List.of("Remplacer les pièces d'usure et requalifier la machine", "Former l'équipe au mode opératoire révisé",
                            "Renforcer le contrôle à réception du fournisseur", "Réparer et contrôler l'étanchéité du quai", "Mettre à jour la recette et valider"))
                            : pick(List.of("Intégrer le contrôle au plan de maintenance préventive", "Mettre à jour la procédure et l'analyse de risques",
                            "Audit fournisseur et plan d'amélioration", "Ajouter un contrôle automatique en ligne", "Sensibilisation de toutes les équipes")));
                    c.setDescription("Action issue de la non-conformité " + nc.getReference());
                    c.setPriority("CRITICAL".equals(nc.getSeverity()) ? "CRITICAL" : "MAJOR".equals(nc.getSeverity()) ? "HIGH" : "MEDIUM");
                    c.setSourceType("NON_CONFORMITY");
                    c.setSourceId(nc.getId());
                    c.setSiteId(nc.getSiteId());
                    User capaOwner = k == 0 && "quality".equals(ownerKey) && chance(0.4) ? user("employee") : owner;
                    c.setOwnerUserId(capaOwner.getId());
                    c.setOwnerName(capaOwner.fullName());
                    LocalDate created = nc.getDetectedAt().atZone(ZONE).toLocalDate().plusDays(3);
                    c.setDueDate(created.plusDays(k == 0 ? 14 : 45));
                    String cs = switch (current) {
                        case "CLOSED" -> "CLOSED";
                        case "PENDING_VERIFICATION" -> chance(0.5) ? "CLOSED" : "PENDING_VERIFICATION";
                        default -> c.getDueDate().isBefore(today) ? pick(List.of("IN_PROGRESS", "OPEN", "IN_PROGRESS")) : pick(List.of("OPEN", "IN_PROGRESS"));
                    };
                    c.setStatus(cs);
                    if (Set.of("PENDING_VERIFICATION", "CLOSED").contains(cs)) {
                        c.setCompletedAt(at(c.getDueDate().minusDays(rnd.nextInt(6)), 15, 0));
                        c.setEvidenceNote("Photos avant/après et enregistrement de formation joints");
                    }
                    if ("CLOSED".equals(cs)) {
                        c.setVerifiedAt(c.getCompletedAt().plus(Duration.ofDays(5)));
                        c.setVerifiedBy("Amina Khelifi");
                        c.setEffectivenessResult("EFFECTIVE");
                        c.setEffectivenessComment("Aucune récurrence constatée sur 30 jours");
                    }
                    capaList.add(c);
                }
            }
            if ("CLOSED".equals(current)) nc.setClosedAt(t);
            if (nc.getCustomFields() == null) nc.setCustomFields(Map.of("shift", pick(List.of("Matin", "Après-midi", "Nuit"))));
        }
        ncs.saveAll(ncList);
        capas.saveAll(capaList);
        for (CapaAction c : capaList) {
            Instant t0 = at(c.getDueDate().minusDays(14), 9, 0);
            event("CapaAction", c.getId(), null, "OPEN", "create", "Amina Khelifi", t0, null);
            if (!"OPEN".equals(c.getStatus())) event("CapaAction", c.getId(), "OPEN", "IN_PROGRESS", "start", c.getOwnerName(), t0.plus(Duration.ofDays(1)), null);
            if (Set.of("PENDING_VERIFICATION", "CLOSED").contains(c.getStatus()))
                event("CapaAction", c.getId(), "IN_PROGRESS", "PENDING_VERIFICATION", "submit", c.getOwnerName(), c.getCompletedAt(), c.getEvidenceNote());
            if ("CLOSED".equals(c.getStatus()))
                event("CapaAction", c.getId(), "PENDING_VERIFICATION", "CLOSED", "verify_effective", "Amina Khelifi", c.getVerifiedAt(), c.getEffectivenessComment());
        }
    }

    private Inspection inspection(int seq, String templateCode, Site site, LocalDate d, Supplier supplier, ProductionLine line, Warehouse wh, Vehicle v, String inspector) {
        InspectionTemplate t = templateByCode.get(templateCode);
        Inspection i = new Inspection();
        i.setReference("INS-" + d.getYear() + "-" + String.format("%05d", seq));
        i.setTemplateId(t.getId());
        i.setDomain(t.getDomain());
        i.setInspectionType(t.getInspectionType());
        i.setSiteId(site.getId());
        i.setScheduledDate(d);
        i.setInspectorName(inspector);
        i.setTitle(t.getName() + switch (templateCode) {
            case "QC-INCOMING" -> supplier == null ? "" : " — " + supplier.getName();
            case "QC-INPROCESS", "QC-FINISHED" -> line == null ? " — " + site.getName() : " — " + line.getCode() + " " + line.getName();
            case "HY-WAREHOUSE" -> wh == null ? " — " + site.getName() : " — " + wh.getName();
            case "HY-VEHICLE" -> v == null ? "" : " — " + v.getPlateNumber();
            default -> " — " + site.getName();
        });
        if (supplier != null) i.setSupplierId(supplier.getId());
        if (line != null) {
            i.setProductionLineId(line.getId());
            ProductDef pd = PRODUCTS.stream().filter(p -> p.line().equals(line.getCode())).findFirst().orElseThrow();
            Batch b = batchFor(pd.sku(), d.plusDays(3));
            if (b != null) {
                i.setBatchId(b.getId());
                i.setProductId(b.getProductId());
            }
        }
        if (wh != null) i.setWarehouseId(wh.getId());
        if (v != null) i.setVehicleId(v.getId());
        double failP = 0.03;
        if (supplier != null && supplier.getCode().equals("SUP-003")) failP = 0.12;
        if (supplier != null && supplier.getCode().equals("SUP-008")) failP = 0.08;
        if (supplier != null && supplier.getCode().equals("SUP-012")) failP = 0.15;
        if (line != null && line.getCode().equals("L2") && ChronoUnit.DAYS.between(d, today) < 60) failP = 0.1;
        if (wh != null && wh.getCode().equals("WH-ORN")) failP = 0.06;
        List<Map<String, Object>> answers = new ArrayList<>();
        Map<String, Object> given = new HashMap<>();
        for (Map<String, Object> q : t.getQuestions()) {
            if (q.get("condition") instanceof Map<?, ?> cond) {
                Object dep = given.get(String.valueOf(cond.get("questionId")));
                if (dep == null || !String.valueOf(dep).equalsIgnoreCase(String.valueOf(cond.get("equals")))) continue;
            }
            boolean critical = Boolean.TRUE.equals(q.get("critical"));
            boolean pass = !chance(critical ? failP / 3 : failP);
            Object value = answerFor(q, pass);
            if (value == null) continue;
            String id = String.valueOf(q.get("id"));
            given.put(id, value);
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("questionId", id);
            a.put("value", value);
            if (!pass) a.put("comment", "Écart observé lors du contrôle");
            answers.add(a);
        }
        ChecklistScorer.Evaluation ev = ChecklistScorer.evaluate(t.getQuestions(), answers, t.getPassingScore());
        i.setAnswers(ev.answers());
        i.setScore(ev.score());
        i.setResult(ev.result());
        i.setStatus("COMPLETED");
        i.setCompletedAt(randomInstant(d));
        i.setSignature("signed:" + inspector);
        if (templateCode.startsWith("HY-WAREHOUSE") || templateCode.equals("QC-INCOMING")) {
            i.setTemperature(Math.round(jitter(19, 3) * 10) / 10.0);
            i.setHumidity(Math.round(jitter(52, 8) * 10) / 10.0);
        }
        i.setGeneratedNcCount(0);
        return i;
    }

    private NonConformity baseNc(int seq, Instant detected) {
        NonConformity nc = new NonConformity();
        nc.setDetectedAt(detected);
        nc.setReportedByName(personName());
        nc.setSource("EMPLOYEE");
        return nc;
    }

    private NonConformity fromSpec(int seq, NcSpec s, LocalDate d, ProductionLine line) {
        if (d.isAfter(today)) d = today;
        NonConformity nc = baseNc(seq, randomInstant(d).isAfter(Instant.now()) ? Instant.now().minus(Duration.ofHours(2)) : randomInstant(d));
        nc.setTitle(s.title());
        nc.setDescription(s.description());
        nc.setType(s.type());
        nc.setCategory(s.category());
        nc.setSeverity(s.severity());
        nc.setImmediateAction(s.action());
        nc.setRootCause(s.rootCause());
        nc.setFiveWhys(s.whys());
        nc.setSource(s.type().equals("CUSTOMER_COMPLAINT") ? "CUSTOMER" : s.type().equals("SUPPLIER") ? "SUPPLIER" : "EMPLOYEE");
        if (s.type().equals("SUPPLIER")) {
            nc.setSupplierId(supplierByCode.get("SUP-003").getId());
            nc.setSiteId(siteByCode.get("CST").getId());
        }
        if (s.type().equals("HYGIENE")) nc.setSiteId(pick(List.of(siteByCode.get("ALG"), siteByCode.get("ORN"), siteByCode.get("CST"))).getId());
        if (s.type().equals("ENVIRONMENT")) nc.setSiteId(siteByCode.get(pick(List.of("CST", "STF"))).getId());
        if (line != null && !s.type().equals("SUPPLIER") && !s.type().equals("HYGIENE") && !s.type().equals("ENVIRONMENT")) {
            nc.setProductionLineId(line.getId());
            nc.setSiteId(line.getSiteId());
            ProductDef pd = pick(PRODUCTS.stream().filter(p -> p.line().equals(line.getCode())).toList());
            nc.setProductId(productBySku.get(pd.sku()).getId());
            Batch b = batchFor(pd.sku(), d.plusDays(3));
            if (b != null) nc.setBatchId(b.getId());
            nc.setQuantityAffected(s.severity().equals("MAJOR") ? 200 + rnd.nextInt(900) : 10 + rnd.nextInt(150));
        }
        nc.setCostEstimate(BigDecimal.valueOf(20000 + rnd.nextInt(400000)));
        return nc;
    }

    // =============================================================== incidents
    private void seedIncidents() {
        String[][] safety = {
                {"NEAR_MISS", "Chariot élévateur — quasi-collision avec un piéton au quai d'expédition", "Collision évitée de justesse ; piéton hors allée balisée.", "2", "4"},
                {"NEAR_MISS", "Chute d'un carton depuis le 4e niveau de rack", "Carton mal positionné tombé dans l'allée, personne à proximité.", "2", "3"},
                {"NEAR_MISS", "Protecteur de presse ouvert pendant le fonctionnement", "Interverrouillage contourné détecté lors d'une ronde.", "4", "3"},
                {"FIRST_AID", "Coupure à la main lors du changement de lame", "Opérateur coupé à l'index, soins sur place.", "2", "3"},
                {"FIRST_AID", "Brûlure légère au four biscuits", "Contact avec une plaque chaude lors du nettoyage.", "2", "3"},
                {"ACCIDENT", "Entorse de la cheville — chute de plain-pied zone lavage", "Sol glissant après nettoyage, absence de signalisation.", "3", "3"},
                {"ACCIDENT", "Écrasement du pied par transpalette", "Transpalette électrique, chaussures de sécurité portées.", "3", "2"},
                {"ACCIDENT", "Lombalgie lors de la palettisation manuelle", "Manutention répétitive de cartons de 12 kg.", "3", "4"},
                {"DANGEROUS_SITUATION", "Armoire électrique laissée ouverte", "Armoire TGBT ouverte sans balisage.", "4", "2"},
                {"DANGEROUS_SITUATION", "Issue de secours encombrée par des palettes", "Palettes vides stockées devant une issue.", "4", "3"},
                {"PROPERTY_DAMAGE", "Rack endommagé par un chariot", "Montant de rack plié, zone condamnée.", "3", "3"}};
        String[][] env = {{"SPILL", "CHEMICAL", "Déversement de soude caustique — station NEP", "Fuite d'environ 40 L sur dalle, contenue par le bac de rétention."},
                {"SPILL", "WATER", "Fuite d'huile hydraulique vers le réseau pluvial", "Flexible percé sur presse, traces d'huile au regard pluvial."},
                {"EMISSION", "AIR", "Fumées noires à la cheminée de la chaudière", "Mauvais réglage du brûleur après maintenance."},
                {"SPILL", "WASTE", "Débordement de la benne déchets alimentaires", "Collecte non effectuée pendant 3 jours."},
                {"EMISSION", "ENERGY", "Fuite importante d'air comprimé — réseau Sétif", "Fuite détectée par ultrasons, perte estimée 15 kW."}};
        String[][] hyg = {{"DANGEROUS_SITUATION", "Rupture de la chaîne du froid — quai Alger", "Température quai mesurée à 29 °C pendant 2 h."},
                {"DANGEROUS_SITUATION", "Lave-mains hors service à l'entrée production", "Absence d'eau chaude au poste de lavage des mains."}};
        List<Incident> list = new ArrayList<>();
        int seq = 0;
        for (int day = 365; day >= 0; day -= 1) {
            LocalDate d = today.minusDays(day);
            if (chance(0.2)) list.add(incident(++seq, "SAFETY", pick(Arrays.asList(safety)), d, null));
            if (chance(0.03)) list.add(incident(++seq, "ENVIRONMENT", null, d, pick(Arrays.asList(env))));
            if (chance(0.015)) {
                String[] h = pick(Arrays.asList(hyg));
                list.add(incident(++seq, "HYGIENE", new String[]{h[0], h[1], h[2], "3", "3"}, d, null));
            }
        }
        // one serious recent accident awaiting investigation
        Incident serious = incident(++seq, "SAFETY", new String[]{"ACCIDENT", "Main happée par le convoyeur — ligne couches L7",
                "Opérateur blessé à la main droite lors d'un débourrage sans consignation. Évacué vers le CHU de Sétif.", "4", "3"}, today.minusDays(1), null);
        serious.setLostTimeDays(12);
        serious.setInjuredPersons(1);
        serious.setSiteId(siteByCode.get("STF").getId());
        list.add(serious);
        incidents.saveAll(list);
        List<CapaAction> capaList = new ArrayList<>();
        int capaSeq = 5000;
        for (Incident i : list) {
            long age = ChronoUnit.DAYS.between(i.getOccurredAt().atZone(ZONE).toLocalDate(), today);
            boolean isEnv = "ENVIRONMENT".equals(i.getDomain());
            String[] path = isEnv ? new String[]{"IMPACT_ASSESSMENT", "UNDER_INVESTIGATION", "ACTIONS_IN_PROGRESS", "CLOSED"}
                    : new String[]{"UNDER_INVESTIGATION", "ACTIONS_IN_PROGRESS", "PENDING_VERIFICATION", "CLOSED"};
            String[] codes = isEnv ? new String[]{"assess_impact", "start_investigation", "document_root_cause", "close"}
                    : new String[]{"start_investigation", "document_root_cause", "submit_verification", "close"};
            int steps = i == serious ? 0 : age > 40 ? 4 : age > 15 ? 2 + rnd.nextInt(3) : age > 3 ? rnd.nextInt(3) : 0;
            boolean minorClose = !isEnv && "NEAR_MISS".equals(i.getType()) && age > 10 && chance(0.4);
            Instant t = i.getOccurredAt().plus(Duration.ofHours(1));
            event("Incident", i.getId(), null, "REPORTED", "create", i.getReportedByName(), t, null);
            String current = "REPORTED";
            if (minorClose) {
                event("Incident", i.getId(), "REPORTED", "CLOSED", "close_minor", "Nadia Belkacem", t.plus(Duration.ofDays(2)), "Mesure immédiate suffisante, partage en quart d'heure sécurité");
                current = "CLOSED";
                i.setClosedAt(t.plus(Duration.ofDays(2)));
            } else {
                for (int k = 0; k < steps; k++) {
                    t = t.plus(Duration.ofHours(24 + rnd.nextInt(96)));
                    event("Incident", i.getId(), current, path[k], codes[k], k == 0 || k == 3 ? "Nadia Belkacem" : "Hichem Bouzid", t,
                            k == 3 ? "Mesures vérifiées, incident clôturé" : null);
                    current = path[k];
                }
            }
            i.setStatus(current);
            if (!"REPORTED".equals(current)) {
                i.setInvestigatorName(isEnv ? "Farid Zerrouki" : "Nadia Belkacem");
                i.setInvestigatorUserId(user(isEnv ? "environment" : "safety").getId());
            }
            if (Set.of("ACTIONS_IN_PROGRESS", "PENDING_VERIFICATION", "CLOSED").contains(current) && !minorClose) {
                i.setRootCause(pick(List.of("Mode opératoire non respecté (absence de consignation)", "Balisage et signalisation insuffisants",
                        "Défaut de maintenance préventive", "Formation au poste non réalisée pour un intérimaire", "Organisation du flux piétons/engins inadaptée")));
                i.setInvestigationFindings("Analyse arbre des causes réalisée avec l'équipe ; témoignages recueillis.");
                CapaAction c = new CapaAction();
                c.setReference("CAPA-" + i.getOccurredAt().atZone(ZONE).getYear() + "-" + (++capaSeq));
                c.setType("CORRECTIVE");
                c.setTitle(pick(List.of("Mettre en place un balisage au sol et des barrières", "Former le personnel à la consignation (LOTO)",
                        "Installer un capteur de sécurité supplémentaire", "Réviser la procédure et afficher le mode opératoire", "Mettre à jour le plan de circulation")));
                c.setPriority(i.getSeverity() >= 4 ? "HIGH" : "MEDIUM");
                c.setSourceType("INCIDENT");
                c.setSourceId(i.getId());
                c.setSiteId(i.getSiteId());
                User owner = user(isEnv ? "environment" : "safety");
                c.setOwnerUserId(owner.getId());
                c.setOwnerName(owner.fullName());
                c.setDueDate(i.getOccurredAt().atZone(ZONE).toLocalDate().plusDays(30));
                c.setStatus("CLOSED".equals(current) || "PENDING_VERIFICATION".equals(current) ? "CLOSED"
                        : c.getDueDate().isBefore(today) ? "IN_PROGRESS" : pick(List.of("OPEN", "IN_PROGRESS")));
                if ("CLOSED".equals(c.getStatus())) {
                    c.setCompletedAt(t.minus(Duration.ofDays(2)));
                    c.setVerifiedAt(t.minus(Duration.ofDays(1)));
                    c.setVerifiedBy("Amina Khelifi");
                    c.setEffectivenessResult("EFFECTIVE");
                    c.setEvidenceNote("Photos de la mise en œuvre jointes");
                }
                capaList.add(c);
            }
            if ("CLOSED".equals(current) && i.getClosedAt() == null) i.setClosedAt(t);
        }
        incidents.saveAll(list);
        capas.saveAll(capaList);
    }

    private Incident incident(int seq, String domain, String[] safety, LocalDate d, String[] env) {
        Incident i = new Incident();
        i.setReference("INC-" + d.getYear() + "-" + String.format("%04d", seq));
        i.setDomain(domain);
        Site s = "ENVIRONMENT".equals(domain) ? siteByCode.get(pick(List.of("CST", "STF"))) : pick(new ArrayList<>(siteByCode.values()));
        i.setSiteId(s.getId());
        String deptCode = s.getCode() + "-" + pick(List.of("PROD", "WH", "LOG", "MAINT"));
        Department dep = deptByCode.get(deptCode);
        if (dep != null) i.setDepartmentId(dep.getId());
        if (env != null) {
            i.setType(env[0]);
            i.setEnvCategory(env[1]);
            i.setTitle(env[2]);
            i.setDescription(env[3]);
            i.setSeverity(2 + rnd.nextInt(2));
            i.setProbability(2 + rnd.nextInt(2));
            i.setImpactAssessment("Impact limité au site, pas de rejet hors enceinte constaté.");
        } else {
            i.setType(safety[0]);
            i.setTitle(safety[1]);
            i.setDescription(safety[2]);
            i.setSeverity(Integer.parseInt(safety[3]));
            i.setProbability(Integer.parseInt(safety[4]));
        }
        i.setRiskScore(RiskMatrix.score(i.getProbability(), i.getSeverity()));
        i.setLocationDetail(pick(List.of("Quai d'expédition", "Zone de conditionnement", "Atelier maintenance", "Allée principale", "Zone lavage", "Magasin MP", "Parking camions")));
        Instant when = randomInstant(d);
        i.setOccurredAt(when.isAfter(Instant.now()) ? Instant.now().minus(Duration.ofHours(3)) : when);
        i.setReportedByName(personName());
        i.setInjuredPersons("ACCIDENT".equals(i.getType()) || "FIRST_AID".equals(i.getType()) ? 1 : 0);
        i.setLostTimeDays("ACCIDENT".equals(i.getType()) ? (chance(0.6) ? 1 + rnd.nextInt(15) : 0) : 0);
        i.setImmediateActions(pick(List.of("Zone sécurisée et balisée", "Premiers soins prodigués", "Machine arrêtée et consignée", "Absorbants déployés", "Information du responsable de poste")));
        i.setDueDate(i.getOccurredAt().atZone(ZONE).toLocalDate().plusDays(i.getSeverity() >= 4 ? 7 : 21));
        i.setCustomFields(Map.of("witness", personName()));
        return i;
    }

    // =============================================================== risks & permits
    private void seedRisks() {
        Object[][] defs = {
                {"Happement / coincement — presse à pâtes L1", "MECHANICAL", "Machine safety", "CST", 3, 5, 2, 4, "EQ-L1-PRS"},
                {"Coincement dans l'ensacheuse verticale L2", "MECHANICAL", "Machine safety", "CST", 4, 4, 2, 4, "EQ-L2-PCK"},
                {"Brûlure — four tunnel biscuits", "PHYSICAL", "Occupational safety", "CST", 3, 3, 2, 2, "EQ-L4-OVN"},
                {"Explosion de poussières — silo de semoule", "FIRE", "Fire & explosion", "CST", 2, 5, 1, 5, null},
                {"Chute de plain-pied — zones de lavage", "PHYSICAL", "Occupational safety", "CST", 4, 2, 2, 2, null},
                {"Collision chariot / piéton — quais d'expédition", "TRAFFIC", "Logistics & traffic", "ALG", 4, 4, 2, 4, null},
                {"Chute de charges depuis les racks", "MECHANICAL", "Logistics & traffic", "ALG", 3, 4, 2, 3, null},
                {"Manutention manuelle — palettisation", "ERGONOMIC", "Occupational safety", "ORN", 5, 2, 3, 2, null},
                {"Électrisation — intervention sur armoire TGBT", "ELECTRICAL", "Occupational safety", "STF", 2, 5, 1, 4, null},
                {"Exposition au bruit — machine à papier PM1", "PHYSICAL", "Occupational safety", "STF", 5, 3, 3, 2, "EQ-L5-PM1"},
                {"Incendie — stockage des bobines de cellulose", "FIRE", "Fire & explosion", "STF", 2, 5, 1, 4, null},
                {"Happement — ligne de transformation couches", "MECHANICAL", "Machine safety", "STF", 3, 5, 2, 4, "EQ-L7-CNV"},
                {"Exposition aux produits de nettoyage (soude, acide)", "CHEMICAL", "Occupational safety", "CST", 3, 3, 2, 2, null},
                {"Contamination par corps étranger métallique", "PHYSICAL", "Product safety", "CST", 2, 5, 1, 5, "EQ-L2-MDT"},
                {"Contamination microbiologique — couscous", "BIOLOGICAL", "Product safety", "CST", 2, 4, 1, 4, null},
                {"Accident routier lors des livraisons", "TRAFFIC", "Logistics & traffic", "ALG", 3, 5, 2, 4, null},
                {"Stress thermique en entrepôt l'été", "PHYSICAL", "Occupational safety", "ORN", 4, 3, 2, 2, null},
                {"Déversement de produits chimiques vers le réseau", "ENVIRONMENTAL", "Environmental", "CST", 2, 4, 1, 3, null},
                {"Rejet d'eaux usées non conformes", "ENVIRONMENTAL", "Environmental", "STF", 3, 3, 2, 3, null},
                {"Risques psychosociaux — travail posté de nuit", "PSYCHOSOCIAL", "Occupational safety", "CST", 3, 3, 2, 3, null},
                {"Chute de hauteur — maintenance en toiture", "PHYSICAL", "Occupational safety", "STF", 2, 5, 1, 4, null},
                {"Asphyxie — nettoyage de silo (espace confiné)", "CHEMICAL", "Occupational safety", "CST", 2, 5, 1, 5, null},
                {"Rupture de la chaîne du froid — quai Alger", "PHYSICAL", "Product safety", "ALG", 3, 3, 2, 2, "EQ-ALG-CLD"},
                {"Panne compresseur entraînant l'arrêt de production", "MECHANICAL", "Machine safety", "STF", 4, 3, 2, 3, "EQ-STF-CMP1"}};
        List<RiskAssessment> list = new ArrayList<>();
        int seq = 0;
        Map<String, Equipment> eq = equipmentList.stream().collect(Collectors.toMap(Equipment::getCode, e -> e));
        for (Object[] d : defs) {
            RiskAssessment r = new RiskAssessment();
            r.setReference("RISK-" + today.getYear() + "-" + String.format("%04d", ++seq));
            r.setTitle((String) d[0]);
            r.setAssessmentType("GENERAL");
            r.setHazardCategory((String) d[1]);
            r.setRiskCategory((String) d[2]);
            r.setSiteId(siteByCode.get((String) d[3]).getId());
            r.setActivity(((String) d[0]).split(" — ")[0]);
            r.setHazard(((String) d[0]));
            r.setProbability((Integer) d[4]);
            r.setSeverity((Integer) d[5]);
            r.setRiskScore(RiskMatrix.score(r.getProbability(), r.getSeverity()));
            r.setRiskLevel(RiskMatrix.level(r.getRiskScore()));
            r.setResidualProbability((Integer) d[6]);
            r.setResidualSeverity((Integer) d[7]);
            r.setResidualScore(RiskMatrix.score(r.getResidualProbability(), r.getResidualSeverity()));
            r.setResidualLevel(RiskMatrix.level(r.getResidualScore()));
            r.setExistingControls("Procédure de poste, EPI, formation à l'embauche");
            r.setPreventiveControls(pick(List.of("Protecteurs fixes et interverrouillés, consignation LOTO", "Plan de circulation, allées piétonnes balisées",
                    "Détection gaz et ventilation, permis de travail", "Aide à la manutention (gerbeur, table élévatrice)", "Audit 5S hebdomadaire")));
            r.setOwnerName(pick(List.of("Nadia Belkacem", "Hichem Bouzid", "Salim Djeddi", "Leila Mansouri", "Farid Zerrouki")));
            r.setReviewDate(today.plusDays(rnd.nextInt(300) - 30));
            r.setStatus(chance(0.85) ? "ACTIVE" : "UNDER_REVIEW");
            if (d[8] != null) r.setEquipmentId(eq.get((String) d[8]).getId());
            list.add(r);
        }
        String[][] jsa = {{"Nettoyage du silo de semoule", "Consignation énergies|Asphyxie, ensevelissement|Permis espace confiné, détection O2, surveillant",
                "Descente dans le silo|Chute|Harnais, trépied, ligne de vie", "Nettoyage|Poussières|Masque FFP3, aspiration"},
                {"Changement de format ensacheuse L2", "Arrêt machine|Démarrage intempestif|Consignation LOTO", "Remplacement mâchoires|Brûlure, coupure|Gants anti-chaleur, outils dédiés",
                        "Redémarrage|Coincement|Vérification protecteurs, essai à vide"},
                {"Chargement d'un semi-remorque", "Mise à quai|Départ intempestif du camion|Cales, cadenas de quai", "Chargement chariot|Chute du chariot|Contrôle niveleur, vitesse limitée"},
                {"Intervention sur armoire électrique", "Préparation|Électrisation|Habilitation BR, VAT", "Intervention|Arc électrique|EPI arc flash, écran facial"},
                {"Travail en toiture (maintenance)", "Accès|Chute de hauteur|Nacelle, harnais", "Intervention|Chute d'objets|Balisage au sol, sac à outils"}};
        for (String[] j : jsa) {
            RiskAssessment r = new RiskAssessment();
            r.setReference("JSA-" + today.getYear() + "-" + String.format("%03d", ++seq));
            r.setTitle("Analyse de sécurité du poste — " + j[0]);
            r.setAssessmentType("JSA");
            r.setActivity(j[0]);
            r.setHazardCategory("MECHANICAL");
            r.setRiskCategory("Occupational safety");
            r.setSiteId(siteByCode.get(pick(List.of("CST", "STF", "ALG"))).getId());
            r.setProbability(3);
            r.setSeverity(4);
            r.setRiskScore(12);
            r.setRiskLevel("HIGH");
            r.setResidualProbability(1);
            r.setResidualSeverity(4);
            r.setResidualScore(4);
            r.setResidualLevel("LOW");
            r.setStatus("ACTIVE");
            r.setOwnerName("Nadia Belkacem");
            r.setReviewDate(today.plusMonths(6));
            List<Map<String, Object>> steps = new ArrayList<>();
            for (int k = 1; k < j.length; k++) {
                String[] p = j[k].split("\\|");
                steps.add(Map.of("step", p[0], "hazards", p[1], "controls", p[2]));
            }
            r.setJsaSteps(steps);
            list.add(r);
        }
        risks.saveAll(list);
        List<WorkPermit> wp = new ArrayList<>();
        String[] types = {"HOT_WORK", "CONFINED_SPACE", "WORK_AT_HEIGHT", "ELECTRICAL", "LOCKOUT_TAGOUT", "CHEMICAL"};
        for (int k = 0; k < 28; k++) {
            WorkPermit p = new WorkPermit();
            p.setReference("PTW-" + today.getYear() + "-" + String.format("%04d", k + 1));
            p.setPermitType(types[k % types.length]);
            p.setSiteId(siteByCode.get(pick(List.of("CST", "STF"))).getId());
            Equipment e = pick(equipmentList);
            p.setEquipmentId(e.getId());
            p.setDescription(switch (p.getPermitType()) {
                case "HOT_WORK" -> "Soudure sur châssis — " + e.getName();
                case "CONFINED_SPACE" -> "Nettoyage intérieur silo / trémie";
                case "WORK_AT_HEIGHT" -> "Remplacement éclairage hall de production";
                case "ELECTRICAL" -> "Remplacement disjoncteur armoire " + e.getCode();
                case "LOCKOUT_TAGOUT" -> "Changement de pièces d'usure — " + e.getName();
                default -> "Transfert de produits chimiques NEP";
            });
            p.setRequestedBy(pick(List.of("K. Benali", "S. Haddad", "M. Boudiaf")));
            p.setContractor(chance(0.4) ? pick(List.of("Électro-Est SARL", "Soudure Rhumel", "Clean Indus")) : null);
            LocalDate d = today.minusDays(120 - k * 4L);
            p.setValidFrom(at(d, 8, 0));
            p.setValidTo(at(d, 17, 0));
            p.setStatus(d.isBefore(today) ? "CLOSED" : d.equals(today) ? "ACTIVE" : "APPROVED");
            p.setApprovedBy("Nadia Belkacem");
            p.setPrecautions(List.of("Consignation vérifiée", "Extincteur à proximité", "Balisage de zone", "Surveillant désigné"));
            p.setGasTestResult(p.getPermitType().equals("CONFINED_SPACE") ? "O2 20,9 % — LIE 0 % — H2S 0 ppm" : null);
            wp.add(p);
        }
        permits.saveAll(wp);
    }

    // =============================================================== audits
    private void seedAudits() {
        Object[][] defs = {{"Audit interne ISO 9001 — Constantine", "INTERNAL", "ISO_9001", "CST", null, 320, 86.0},
                {"Audit interne ISO 9001 — Sétif", "INTERNAL", "ISO_9001", "STF", null, 260, 82.0},
                {"Audit interne ISO 14001 — Groupe", "INTERNAL", "ISO_14001", "CST", null, 200, 79.0},
                {"Audit interne ISO 45001 — Centre de distribution Alger", "INTERNAL", "ISO_45001", "ALG", null, 150, 81.0},
                {"Audit HACCP — Ligne couscous", "INTERNAL", "HACCP", "CST", null, 110, 90.0},
                {"Audit fournisseur — PlastEmball SARL", "SUPPLIER", "ISO_9001", null, "SUP-003", 95, 62.0},
                {"Audit fournisseur — Cacao Ivoire SA", "SUPPLIER", "ISO_22000", null, "SUP-008", 170, 74.0},
                {"Audit fournisseur — Nordic Pulp AB", "SUPPLIER", "ISO_14001", null, "SUP-005", 240, 94.0},
                {"Audit de surveillance ISO 9001 — CertiQual Algérie", "CERTIFICATION", "ISO_9001", "CST", null, 60, 88.0},
                {"Audit interne ISO 45001 — Sétif", "INTERNAL", "ISO_45001", "STF", null, 35, 77.0}};
        int seq = 0, fseq = 0;
        List<NonConformity> auditNcs = new ArrayList<>();
        for (Object[] d : defs) {
            Audit a = new Audit();
            a.setReference("AUD-" + today.getYear() + "-" + String.format("%03d", ++seq));
            a.setTitle((String) d[0]);
            a.setAuditType((String) d[1]);
            a.setStandard((String) d[2]);
            if (d[3] != null) a.setSiteId(siteByCode.get((String) d[3]).getId());
            if (d[4] != null) a.setSupplierId(supplierByCode.get((String) d[4]).getId());
            LocalDate start = today.minusDays((Integer) d[5]);
            a.setPlannedDate(start);
            a.setStartDate(start);
            a.setEndDate(start.plusDays(1));
            a.setLeadAuditor(d[1].equals("CERTIFICATION") ? "M. Lakhdari (CertiQual Algérie)" : "Samia Ait Ahmed");
            a.setAuditTeam("Samia Ait Ahmed, Karim Boumediene");
            a.setScope("Processus " + switch ((String) d[2]) {
                case "ISO_14001" -> "environnementaux : déchets, énergie, rejets";
                case "ISO_45001" -> "santé-sécurité : risques, EPI, formation, incidents";
                case "HACCP" -> "HACCP : CCP, PRP, traçabilité";
                default -> "qualité : production, contrôle, achats, réclamations";
            });
            a.setStatus("COMPLETED");
            a.setScore((Double) d[6]);
            a.setConclusion(a.getScore() >= 85 ? "Système efficace, quelques axes d'amélioration." : a.getScore() >= 75
                    ? "Système globalement conforme, écarts à traiter sous 30 jours." : "Écarts significatifs, plan d'actions exigé et audit de suivi.");
            a = audits.save(a);
            int nf = 3 + rnd.nextInt(4);
            for (int k = 0; k < nf; k++) {
                AuditFinding f = new AuditFinding();
                f.setAuditId(a.getId());
                f.setReference("AF-" + today.getYear() + "-" + String.format("%04d", ++fseq));
                String type = a.getScore() < 70 && k < 2 ? "MAJOR_NC" : pick(List.of("MINOR_NC", "OBSERVATION", "OPPORTUNITY", "CONFORMITY", "OBSERVATION"));
                f.setFindingType(type);
                f.setClause(pick(List.of("7.2", "7.5.3", "8.4.2", "8.5.1", "9.1.1", "10.2", "6.1.2", "8.1")));
                f.setDescription(pick(List.of("Enregistrements de formation incomplets pour 3 intérimaires", "Étalonnage de la balance non tracé",
                        "Évaluation des fournisseurs non mise à jour", "Analyse de risques non révisée après modification de ligne",
                        "Bonne pratique : tableau de bord SST affiché et à jour", "Registre des déchets dangereux incomplet",
                        "Absence de preuve d'efficacité pour 2 actions correctives")));
                f.setEvidence("Constat sur échantillon lors de l'audit du " + start);
                f.setStatus(Set.of("MAJOR_NC", "MINOR_NC").contains(type) ? pick(List.of("CLOSED", "IN_PROGRESS")) : "CLOSED");
                f = findings.save(f);
                if (Set.of("MAJOR_NC", "MINOR_NC").contains(type)) {
                    NonConformity nc = baseNc(0, at(start.plusDays(1), 16, 0));
                    nc.setReference("NC-A-" + f.getReference());
                    nc.setTitle("Audit finding " + f.getReference() + " — clause " + f.getClause());
                    nc.setDescription(f.getDescription());
                    nc.setType(a.getAuditType().equals("SUPPLIER") ? "SUPPLIER" : "AUDIT");
                    nc.setCategory(a.getStandard());
                    nc.setSeverity(type.equals("MAJOR_NC") ? "MAJOR" : "MINOR");
                    nc.setSource("AUDIT");
                    nc.setAuditId(a.getId());
                    nc.setSiteId(a.getSiteId());
                    nc.setSupplierId(a.getSupplierId());
                    nc.setReportedByName(a.getLeadAuditor());
                    nc.setDueDate(start.plusDays(31));
                    nc.setStatus("CLOSED".equals(f.getStatus()) ? "CLOSED" : "ACTION_PLAN");
                    nc.setAssigneeName("Karim Boumediene");
                    nc.setAssigneeUserId(user("quality").getId());
                    nc.setRootCause("Exigence connue mais non intégrée au processus");
                    if ("CLOSED".equals(nc.getStatus())) nc.setClosedAt(at(start.plusDays(30), 12, 0));
                    nc = ncs.save(nc);
                    event("NonConformity", nc.getId(), null, "REPORTED", "create", a.getLeadAuditor(), nc.getDetectedAt(), null);
                    event("NonConformity", nc.getId(), "REPORTED", nc.getStatus(), "audit_follow_up", "Karim Boumediene", nc.getDetectedAt().plus(Duration.ofDays(2)), null);
                    f.setNonConformityId(nc.getId());
                    findings.save(f);
                    auditNcs.add(nc);
                }
            }
        }
        Object[][] planned = {{"Audit interne ISO 14001 — Sétif", "INTERNAL", "ISO_14001", "STF", 12}, {"Audit de certification ISO 45001 — Groupe", "CERTIFICATION", "ISO_45001", "CST", 38},
                {"Audit fournisseur — Encres & Codage DZ", "SUPPLIER", "ISO_9001", null, 21}, {"Audit HACCP — Centre de distribution Oran", "INTERNAL", "HACCP", "ORN", 6}};
        for (Object[] p : planned) {
            Audit a = new Audit();
            a.setReference("AUD-" + today.getYear() + "-" + String.format("%03d", ++seq));
            a.setTitle((String) p[0]);
            a.setAuditType((String) p[1]);
            a.setStandard((String) p[2]);
            if (p[3] != null) a.setSiteId(siteByCode.get((String) p[3]).getId());
            else a.setSupplierId(supplierByCode.get("SUP-012").getId());
            a.setPlannedDate(today.plusDays((Integer) p[4]));
            a.setLeadAuditor("Samia Ait Ahmed");
            a.setStatus("PLANNED");
            a.setScope("Selon programme d'audit annuel");
            audits.save(a);
        }
    }

    // =============================================================== documents
    private void seedDocuments() {
        Object[][] defs = {
                {"POL-QHSE-001", "Politique QHSE du groupe Atlas", "POLICY", "QHSE", "APPROVED", 400, null, "PUBLIC", "ISO 9001 §5.2"},
                {"POL-HSE-002", "Politique de prévention des addictions", "POLICY", "SAFETY", "APPROVED", 300, null, "INTERNAL", "ISO 45001 §5.2"},
                {"PRO-QUA-010", "Maîtrise des produits non conformes", "PROCEDURE", "QUALITY", "APPROVED", 200, null, "INTERNAL", "ISO 9001 §8.7"},
                {"PRO-QUA-011", "Gestion des réclamations clients", "PROCEDURE", "QUALITY", "APPROVED", 180, null, "INTERNAL", "ISO 9001 §9.1.2"},
                {"PRO-QUA-012", "Rappel et retrait de produits", "PROCEDURE", "QUALITY", "APPROVED", 150, null, "INTERNAL", "ISO 22000 §8.9.5"},
                {"PRO-QUA-013", "Évaluation et sélection des fournisseurs", "PROCEDURE", "QUALITY", "IN_REVIEW", 20, null, "INTERNAL", "ISO 9001 §8.4"},
                {"PRO-HSE-020", "Permis de travail et consignation", "PROCEDURE", "SAFETY", "APPROVED", 260, null, "INTERNAL", "ISO 45001 §8.1"},
                {"PRO-HSE-021", "Déclaration et analyse des incidents", "PROCEDURE", "SAFETY", "APPROVED", 230, null, "INTERNAL", "ISO 45001 §10.2"},
                {"PRO-ENV-030", "Gestion des déchets", "PROCEDURE", "ENVIRONMENT", "APPROVED", 210, null, "INTERNAL", "ISO 14001 §8.1"},
                {"PRO-HYG-040", "Plan de nettoyage et désinfection", "PROCEDURE", "HYGIENE", "APPROVED", 120, null, "INTERNAL", "HACCP PRP"},
                {"PRO-HYG-041", "Lutte contre les nuisibles", "PROCEDURE", "HYGIENE", "APPROVED", 330, null, "INTERNAL", "HACCP PRP"},
                {"IT-PRD-101", "Changement de format ensacheuse L2", "WORK_INSTRUCTION", "QUALITY", "DRAFT", 3, null, "INTERNAL", null},
                {"IT-PRD-102", "Test du détecteur de métaux", "WORK_INSTRUCTION", "QUALITY", "APPROVED", 90, null, "INTERNAL", "HACCP CCP2"},
                {"IT-HSE-110", "Consignation de l'ensacheuse verticale", "WORK_INSTRUCTION", "SAFETY", "APPROVED", 60, null, "INTERNAL", null},
                {"IT-LOG-120", "Chargement et arrimage des semi-remorques", "WORK_INSTRUCTION", "SAFETY", "APPROVED", 140, null, "INTERNAL", null},
                {"MAN-QHSE-001", "Manuel du système de management intégré", "MANUAL", "QHSE", "APPROVED", 380, null, "INTERNAL", "ISO 9001/14001/45001"},
                {"CERT-ISO9001", "Certificat ISO 9001:2015 — Atlas Industries", "CERTIFICATE", "QUALITY", "APPROVED", 700, 41, "PUBLIC", null},
                {"CERT-HALAL", "Certificat halal — gamme alimentaire", "CERTIFICATE", "QUALITY", "APPROVED", 300, 65, "PUBLIC", null},
                {"FDS-NAOH", "FDS — Soude caustique 30 %", "SAFETY_DATA_SHEET", "SAFETY", "APPROVED", 500, 12, "INTERNAL", null},
                {"FDS-PERAC", "FDS — Acide peracétique 15 %", "SAFETY_DATA_SHEET", "SAFETY", "APPROVED", 420, 210, "INTERNAL", null},
                {"FDS-INK", "FDS — Encre de marquage jet d'encre", "SAFETY_DATA_SHEET", "SAFETY", "APPROVED", 900, -20, "INTERNAL", null},
                {"RPT-ENV-2025", "Rapport environnemental annuel 2025", "REPORT", "ENVIRONMENT", "APPROVED", 280, null, "RESTRICTED", "ESG"},
                {"RPT-DIR-Q3", "Revue de direction QHSE — T3", "REPORT", "QHSE", "APPROVED", 45, null, "RESTRICTED", "ISO 9001 §9.3"},
                {"FORM-NC-001", "Formulaire de déclaration de non-conformité", "FORM", "QUALITY", "APPROVED", 500, null, "PUBLIC", null}};
        for (Object[] d : defs) {
            QhseDocument doc = new QhseDocument();
            doc.setReference((String) d[0]);
            doc.setTitle((String) d[1]);
            doc.setDocType((String) d[2]);
            doc.setDomain((String) d[3]);
            doc.setStatus((String) d[4]);
            doc.setDocVersion(chance(0.4) ? "2.0" : "1." + rnd.nextInt(4));
            doc.setOwnerName(switch ((String) d[3]) {
                case "SAFETY" -> "Nadia Belkacem";
                case "ENVIRONMENT" -> "Farid Zerrouki";
                case "HYGIENE" -> "Lynda Saadi";
                case "QUALITY" -> "Karim Boumediene";
                default -> "Amina Khelifi";
            });
            if ("APPROVED".equals(d[4])) {
                doc.setApproverName("Amina Khelifi");
                doc.setApprovedAt(at(today.minusDays((Integer) d[5]), 11, 0));
                doc.setEffectiveDate(today.minusDays((Integer) d[5] - 2));
                doc.setReviewDate(doc.getEffectiveDate().plusYears(1));
            }
            if (d[6] != null) doc.setExpiryDate(today.plusDays((Integer) d[6]));
            doc.setAccessLevel((String) d[7]);
            if ("RESTRICTED".equals(d[7])) doc.setAllowedRoles(List.of("GENERAL_MANAGER", "QHSE_DIRECTOR", "ENVIRONMENT_MANAGER"));
            doc.setStandardClause((String) d[8]);
            doc.setDescription("Document maîtrisé du système de management intégré Atlas.");
            doc = documents.save(doc);
            event("QhseDocument", doc.getId(), null, "DRAFT", "create", doc.getOwnerName(), at(today.minusDays((Integer) d[5] + 10), 10, 0), null);
            if (!"DRAFT".equals(doc.getStatus())) event("QhseDocument", doc.getId(), "DRAFT", "IN_REVIEW", "submit_review", doc.getOwnerName(), at(today.minusDays((Integer) d[5] + 5), 10, 0), null);
            if ("APPROVED".equals(doc.getStatus())) event("QhseDocument", doc.getId(), "IN_REVIEW", "APPROVED", "approve", "Amina Khelifi", doc.getApprovedAt(), null);
        }
    }

    // =============================================================== training, certifications & PPE
    private void seedTraining() {
        Object[][] defs = {{"TRN-HSE-01", "Accueil sécurité / induction HSE", "SAFETY", true, 24, 4.0}, {"TRN-HYG-01", "Hygiène alimentaire & BPH (HACCP)", "HYGIENE", true, 12, 7.0},
                {"TRN-FIRE-01", "Équipier de première intervention incendie", "SAFETY", true, 12, 3.5}, {"TRN-CACES-03", "Conduite de chariot élévateur (CACES 3)", "SAFETY", false, 60, 21.0},
                {"TRN-ELEC-01", "Habilitation électrique BR/B2V", "SAFETY", false, 36, 21.0}, {"TRN-HGT-01", "Travail en hauteur et port du harnais", "SAFETY", false, 24, 7.0},
                {"TRN-SST-01", "Sauveteur secouriste du travail", "SAFETY", false, 24, 14.0}, {"TRN-ERG-01", "Gestes et postures", "SAFETY", false, 36, 3.5},
                {"TRN-CHEM-01", "Manipulation des produits chimiques", "ENVIRONMENT", false, 24, 3.5}, {"TRN-ISO-01", "Sensibilisation ISO 9001 / 14001 / 45001", "QUALITY", false, 36, 3.0},
                {"TRN-ECO-01", "Éco-conduite des poids lourds", "ENVIRONMENT", false, 24, 7.0}, {"TRN-5S-01", "Démarche 5S", "QUALITY", false, 0, 3.0}};
        List<Training> list = new ArrayList<>();
        for (Object[] d : defs) {
            Training t = new Training();
            t.setCode((String) d[0]);
            t.setTitle((String) d[1]);
            t.setCategory((String) d[2]);
            t.setMandatory((Boolean) d[3]);
            t.setValidityMonths((Integer) d[4]);
            t.setDurationHours((Double) d[5]);
            t.setProvider(chance(0.5) ? "Interne — service HSE" : pick(List.of("INPED Boumerdès", "Protection civile", "CertiForm Algérie")));
            t.setDescription("Module du plan de formation annuel Atlas.");
            list.add(t);
        }
        trainings.saveAll(list);
        List<TrainingRecord> records = new ArrayList<>();
        List<Certification> certs = new ArrayList<>();
        for (Employee e : employeeList) {
            for (Training t : list) {
                boolean relevant = Boolean.TRUE.equals(t.getMandatory()) || (t.getCode().equals("TRN-CACES-03") && e.getJobTitle().contains("Cariste"))
                        || (t.getCode().equals("TRN-ELEC-01") && e.getJobTitle().contains("lectro")) || (t.getCode().equals("TRN-ECO-01") && e.getJobTitle().contains("Chauffeur"))
                        || chance(0.08);
                if (!relevant || chance(0.07)) continue;
                TrainingRecord r = new TrainingRecord();
                r.setEmployeeId(e.getId());
                r.setTrainingId(t.getId());
                LocalDate session = today.minusDays(rnd.nextInt(Math.max(60, (t.getValidityMonths() == 0 ? 24 : t.getValidityMonths()) * 30 + 60)));
                r.setSessionDate(session);
                r.setStatus(chance(0.03) ? "FAILED" : "COMPLETED");
                r.setScore(Math.round(jitter(82, 12) * 10) / 10.0);
                if (t.getValidityMonths() > 0 && "COMPLETED".equals(r.getStatus())) r.setExpiryDate(session.plusMonths(t.getValidityMonths()));
                r.setTrainer(t.getProvider());
                r.setCertificateNumber("ATT-" + (100000 + rnd.nextInt(899999)));
                records.add(r);
                if (t.getCode().equals("TRN-CACES-03") || t.getCode().equals("TRN-ELEC-01")) {
                    Certification c = new Certification();
                    c.setEmployeeId(e.getId());
                    c.setName(t.getCode().equals("TRN-CACES-03") ? "CACES R489 catégorie 3" : "Habilitation électrique B2V-BR");
                    c.setCertType("HABILITATION");
                    c.setIssuer(t.getProvider());
                    c.setNumber("HAB-" + (10000 + rnd.nextInt(89999)));
                    c.setIssuedDate(session);
                    c.setExpiryDate(r.getExpiryDate() == null ? session.plusYears(3) : r.getExpiryDate());
                    c.setStatus(com.dnd.smartflow.qhse.web.CertificationController.statusFor(c.getExpiryDate(), null));
                    certs.add(c);
                }
            }
            if (chance(0.6)) {
                Certification m = new Certification();
                m.setEmployeeId(e.getId());
                m.setName("Aptitude médicale au poste");
                m.setCertType("MEDICAL_FITNESS");
                m.setIssuer("Médecine du travail");
                m.setIssuedDate(today.minusDays(rnd.nextInt(380)));
                m.setExpiryDate(m.getIssuedDate().plusYears(1));
                m.setStatus(com.dnd.smartflow.qhse.web.CertificationController.statusFor(m.getExpiryDate(), null));
                certs.add(m);
            }
        }
        trainingRecords.saveAll(records);
        certifications.saveAll(certs);
        Object[][] ppe = {{"EPI-001", "Chaussures de sécurité S3", "Pieds", "EN ISO 20345", 140, 40, 12}, {"EPI-002", "Gants anti-coupure niveau D", "Mains", "EN 388", 600, 150, 1},
                {"EPI-003", "Bouchons d'oreilles moulés", "Auditif", "EN 352-2", 90, 30, 12}, {"EPI-004", "Lunettes de protection", "Yeux", "EN 166", 120, 40, 12},
                {"EPI-005", "Gilet haute visibilité", "Corps", "EN ISO 20471", 25, 30, 12}, {"EPI-006", "Charlotte et cache-barbe (boîte 100)", "Hygiène", "EN 13795", 300, 80, 0},
                {"EPI-007", "Harnais antichute", "Hauteur", "EN 361", 12, 5, 60}, {"EPI-008", "Gants anti-chaleur", "Mains", "EN 407", 45, 20, 6},
                {"EPI-009", "Masque FFP3", "Respiratoire", "EN 149", 220, 60, 0}, {"EPI-010", "Tablier PVC chimique", "Corps", "EN 14605", 18, 10, 12}};
        List<PpeItem> items = new ArrayList<>();
        for (Object[] p : ppe) {
            PpeItem it = new PpeItem();
            it.setCode((String) p[0]);
            it.setName((String) p[1]);
            it.setCategory((String) p[2]);
            it.setStandardRef((String) p[3]);
            it.setStockQuantity((Integer) p[4]);
            it.setMinStock((Integer) p[5]);
            it.setReplacementMonths((Integer) p[6]);
            items.add(it);
        }
        ppeItems.saveAll(items);
        List<PpeIssuance> iss = new ArrayList<>();
        for (int k = 0; k < 220; k++) {
            PpeItem it = pick(items);
            PpeIssuance pi = new PpeIssuance();
            pi.setEmployeeId(pick(employeeList).getId());
            pi.setPpeItemId(it.getId());
            pi.setQuantity(it.getCode().equals("EPI-002") ? 2 + rnd.nextInt(4) : 1);
            pi.setIssuedDate(today.minusDays(rnd.nextInt(300)));
            if (it.getReplacementMonths() > 0) pi.setReplacementDate(pi.getIssuedDate().plusMonths(it.getReplacementMonths()));
            pi.setIssuedBy("Magasin EPI");
            iss.add(pi);
        }
        ppeIssuances.saveAll(iss);
    }

    // =============================================================== hygiene
    private void seedHygiene() {
        String[][] areas = {{"CST", "Ligne pâtes longues — presse et séchoir", "DAILY"}, {"CST", "Ligne pâtes courtes — ensacheuse", "DAILY"},
                {"CST", "Ligne couscous — rouleuse et cuiseur", "DAILY"}, {"CST", "Ligne biscuits — four et refroidisseur", "DAILY"},
                {"CST", "Vestiaires et sanitaires", "DAILY"}, {"CST", "Silos et trémies de semoule", "MONTHLY"}, {"CST", "Réfectoire", "DAILY"},
                {"STF", "Machine à papier PM1 — zone humide", "WEEKLY"}, {"STF", "Ligne couches bébé", "DAILY"}, {"STF", "Vestiaires", "DAILY"},
                {"ALG", "Quais de chargement", "WEEKLY"}, {"ALG", "Allées de stockage", "BIWEEKLY"}, {"ALG", "Chambre tempérée biscuits", "WEEKLY"},
                {"ORN", "Quais de chargement", "WEEKLY"}, {"ORN", "Allées de stockage", "BIWEEKLY"}};
        List<CleaningSchedule> scheds = new ArrayList<>();
        for (String[] a : areas) {
            CleaningSchedule s = new CleaningSchedule();
            s.setArea(a[1]);
            s.setSiteId(siteByCode.get(a[0]).getId());
            s.setFrequency(a[2]);
            s.setMethod("Pré-nettoyage à sec, détergence alcaline, rinçage, désinfection (acide peracétique), séchage.");
            s.setProductsUsed("Détergent alcalin DA-20, Acide peracétique 15 %");
            s.setResponsibleName(personName());
            s.setActive(true);
            scheds.add(s);
        }
        cleaningSchedules.saveAll(scheds);
        List<CleaningRecord> recs = new ArrayList<>();
        for (CleaningSchedule s : scheds) {
            Duration period = com.dnd.smartflow.qhse.web.CleaningRecordController.period(s.getFrequency());
            Instant last = null;
            for (Instant t = Instant.now().minus(Duration.ofDays(60)); t.isBefore(Instant.now()); t = t.plus(period)) {
                if (chance(0.05)) continue; // missed occurrence
                CleaningRecord r = new CleaningRecord();
                r.setScheduleId(s.getId());
                r.setDoneAt(t.plus(Duration.ofMinutes(rnd.nextInt(180))));
                r.setDoneBy(s.getResponsibleName());
                r.setVerified(chance(0.85));
                if (r.getVerified()) r.setVerifiedBy("Lynda Saadi");
                recs.add(r);
                last = r.getDoneAt();
            }
            s.setLastDoneAt(last);
            s.setNextDueAt(last == null ? Instant.now() : last.plus(period));
        }
        cleaningRecords.saveAll(recs);
        cleaningSchedules.saveAll(scheds);
        List<PestControlVisit> visits = new ArrayList<>();
        for (Site s : siteByCode.values()) {
            for (int m = 12; m >= 0; m--) {
                PestControlVisit v = new PestControlVisit();
                v.setSiteId(s.getId());
                v.setProvider("Hygiène 3D Services");
                v.setVisitDate(today.minusMonths(m).withDayOfMonth(Math.min(5 + rnd.nextInt(10), 28)));
                boolean future = v.getVisitDate().isAfter(today);
                v.setBaitStationsChecked(future ? null : 30 + rnd.nextInt(40));
                boolean activity = !future && chance(s.getCode().equals("ORN") ? 0.25 : 0.08);
                v.setActivityFound(activity);
                v.setFindings(activity ? "Consommation sur 2 postes d'appâtage côté quai ; renforcement du dispositif." : future ? null : "Aucune activité constatée.");
                v.setNextVisit(v.getVisitDate().plusMonths(1));
                v.setStatus(future ? "SCHEDULED" : activity ? "ACTION_REQUIRED" : "DONE");
                visits.add(v);
            }
        }
        pestVisits.saveAll(visits);
    }

    // =============================================================== environment
    private void seedEnvironment() {
        List<EnvironmentalReading> list = new ArrayList<>();
        Map<String, double[]> base = Map.of("CST", new double[]{420000, 38000, 9800}, "STF", new double[]{610000, 52000, 21000},
                "ALG", new double[]{95000, 0, 900}, "ORN", new double[]{62000, 0, 600});
        for (Site s : siteByCode.values()) {
            double[] b = base.get(s.getCode());
            for (int m = 17; m >= 1; m--) {
                LocalDate d = today.minusMonths(m).withDayOfMonth(28);
                int month = d.getMonthValue();
                double summer = (month >= 6 && month <= 9) ? 1.12 : 1.0;
                double efficiency = 1 - (17 - m) * 0.004; // gradual energy-efficiency gains
                list.add(reading("ENERGY_ELECTRICITY", s, null, Math.round(b[0] * summer * efficiency * jitter(1, 0.05)), "kWh", d, "IMPORT", null, null));
                if (b[1] > 0) list.add(reading("ENERGY_GAS", s, null, Math.round(b[1] * (month <= 3 || month >= 11 ? 1.15 : 0.95) * jitter(1, 0.06)), "m3", d, "IMPORT", null, null));
                list.add(reading("WATER_CONSUMPTION", s, null, Math.round(b[2] * summer * jitter(1, 0.07)), "m3", d, "IMPORT", null, null));
            }
            if ("PRODUCTION".equals(s.getType())) {
                for (int w = 52; w >= 0; w--) {
                    double ph = chance(0.06) ? jitter(9.0, 0.3) : jitter(7.4, 0.5);
                    list.add(reading("WATER_PH", s, null, Math.round(ph * 10) / 10.0, "pH", today.minusWeeks(w), "MANUAL", 6.5, 8.5));
                }
            }
        }
        for (Warehouse w : whByCode.values()) {
            Site s = siteByCode.values().stream().filter(x -> x.getId().equals(w.getSiteId())).findFirst().orElseThrow();
            for (int day = 60; day >= 0; day--) {
                for (int h : new int[]{8, 15}) {
                    LocalDate d = today.minusDays(day);
                    double temp = jitter(Boolean.TRUE.equals(w.getTemperatureControlled()) ? 18.5 : 20 + (h == 15 ? 3 : 0), 2.2);
                    if (w.getCode().equals("WH-ORN") && day % 17 == 3 && h == 15) temp = 27.5;
                    EnvironmentalReading r = reading("TEMPERATURE", s, w, Math.round(temp * 10) / 10.0, "°C", d, "IOT",
                            Boolean.TRUE.equals(w.getTemperatureControlled()) ? w.getMinTemp() : null, Boolean.TRUE.equals(w.getTemperatureControlled()) ? w.getMaxTemp() : 25.0);
                    r.setRecordedAt(at(d, h, 0));
                    r.setSensorId("TH-" + w.getCode() + "-01");
                    list.add(r);
                    EnvironmentalReading hu = reading("HUMIDITY", s, w, Math.round(jitter(52, 7) * 10) / 10.0, "%", d, "IOT", 30.0, 65.0);
                    hu.setRecordedAt(at(d, h, 0));
                    hu.setSensorId("TH-" + w.getCode() + "-01");
                    list.add(hu);
                }
            }
        }
        readings.saveAll(list);
        Object[][] wasteTypes = {{"Cartons et papiers", "RECYCLABLE", "15 01 01", "RECYCLING", 2200}, {"Films plastiques", "RECYCLABLE", "15 01 02", "RECYCLING", 900},
                {"Déchets de pâte et semoule", "NON_HAZARDOUS", "02 03 04", "COMPOSTING", 1600}, {"Rebuts papier / cellulose", "NON_HAZARDOUS", "03 03 08", "RECOVERY", 2600},
                {"Boues de station d'épuration", "NON_HAZARDOUS", "02 03 05", "LANDFILL", 1300}, {"Huiles usagées", "HAZARDOUS", "13 02 05*", "TREATMENT", 120},
                {"Emballages souillés (encres, solvants)", "HAZARDOUS", "15 01 10*", "INCINERATION", 60}, {"Ordures ménagères assimilées", "NON_HAZARDOUS", "20 03 01", "LANDFILL", 700}};
        List<WasteRecord> wr = new ArrayList<>();
        int seq = 0;
        for (String sc : List.of("CST", "STF", "ALG", "ORN")) {
            double scale = switch (sc) {
                case "STF" -> 1.2;
                case "CST" -> 1.0;
                default -> 0.25;
            };
            for (int wk = 52; wk >= 1; wk--) {
                for (Object[] t : wasteTypes) {
                    if (!sc.equals("CST") && !sc.equals("STF") && !Set.of("Cartons et papiers", "Films plastiques", "Ordures ménagères assimilées").contains(t[0])) continue;
                    if (((String) t[1]).equals("HAZARDOUS") && wk % 4 != 0) continue;
                    WasteRecord w = new WasteRecord();
                    w.setReference("WST-" + today.minusWeeks(wk).getYear() + "-" + String.format("%05d", ++seq));
                    w.setWasteType((String) t[0]);
                    w.setClassification((String) t[1]);
                    w.setWasteCode((String) t[2]);
                    w.setTreatment((String) t[3]);
                    w.setSiteId(siteByCode.get(sc).getId());
                    // recycling performance improves over the year
                    double improvement = "LANDFILL".equals(t[3]) ? 1 - (52 - wk) * 0.006 : 1;
                    w.setQuantityKg((double) Math.round((Integer) t[4] * scale * improvement * jitter(1, 0.2) * ((String) t[1]).length() / ((String) t[1]).length()));
                    w.setCarrier(pick(List.of("EnviroCollect", "Recyclage Est SARL", "SOTRADEM")));
                    w.setDestination(switch ((String) t[3]) {
                        case "RECYCLING" -> "Centre de tri — Recyclage Est";
                        case "COMPOSTING" -> "Plateforme de compostage agricole";
                        case "INCINERATION", "TREATMENT" -> "Centre agréé de traitement des déchets spéciaux";
                        case "RECOVERY" -> "Cartonnerie (valorisation matière)";
                        default -> "CET wilaya";
                    });
                    w.setManifestNumber(((String) t[1]).equals("HAZARDOUS") ? "BSD-" + (100000 + rnd.nextInt(899999)) : null);
                    w.setRecordedDate(today.minusWeeks(wk).plusDays(rnd.nextInt(5)));
                    w.setCost(BigDecimal.valueOf(Math.round(w.getQuantityKg() * (((String) t[1]).equals("HAZARDOUS") ? 45 : 4))));
                    wr.add(w);
                }
            }
        }
        waste.saveAll(wr);
        Object[][] chem = {{"Soude caustique 30 %", "1310-73-2", "Corrosif (H314)", "CST", 2400.0, "L", "GHS05"}, {"Acide peracétique 15 %", "79-21-0", "Comburant, corrosif", "CST", 600.0, "L", "GHS03, GHS05, GHS07"},
                {"Hypochlorite de sodium 12 %", "7681-52-9", "Corrosif, dangereux pour l'environnement", "STF", 800.0, "L", "GHS05, GHS09"},
                {"Détergent alcalin DA-20", "—", "Irritant", "CST", 450.0, "L", "GHS07"}, {"Lubrifiant alimentaire H1", "—", "Non classé", "CST", 120.0, "L", "—"},
                {"Gazole", "68334-30-5", "Inflammable", "ALG", 15000.0, "L", "GHS02, GHS07, GHS08, GHS09"}, {"Huile hydraulique ISO 46", "—", "Dangereux pour l'environnement", "STF", 600.0, "L", "GHS09"},
                {"Encre jet d'encre MEK", "78-93-3", "Inflammable, irritant", "CST", 80.0, "L", "GHS02, GHS07"}, {"Colle hot-melt", "—", "Non classé", "STF", 1200.0, "kg", "—"},
                {"Polymère super-absorbant", "9003-04-7", "Non classé", "STF", 18000.0, "kg", "—"}, {"Fluide frigorigène R134a", "811-97-2", "Gaz sous pression", "ALG", 45.0, "kg", "GHS04"}};
        List<ChemicalProduct> cp = new ArrayList<>();
        for (Object[] c : chem) {
            ChemicalProduct p = new ChemicalProduct();
            p.setName((String) c[0]);
            p.setCasNumber((String) c[1]);
            p.setHazardClass((String) c[2]);
            p.setSiteId(siteByCode.get((String) c[3]).getId());
            p.setStorageLocation(pick(List.of("Local produits chimiques", "Armoire ventilée atelier", "Cuve extérieure sur rétention", "Magasin MP")));
            p.setQuantity((Double) c[4]);
            p.setUnit((String) c[5]);
            p.setGhsPictograms((String) c[6]);
            p.setSdsExpiry(today.plusDays(rnd.nextInt(900) - 60));
            cp.add(p);
        }
        chemicals.saveAll(cp);
    }

    private EnvironmentalReading reading(String type, Site s, Warehouse w, double value, String unit, LocalDate d, String source, Double min, Double max) {
        EnvironmentalReading r = new EnvironmentalReading();
        r.setReadingType(type);
        r.setSiteId(s.getId());
        r.setWarehouseId(w == null ? null : w.getId());
        r.setLocation(w == null ? s.getName() : w.getName());
        r.setReadingValue(value);
        r.setUnit(unit);
        r.setMinThreshold(min);
        r.setMaxThreshold(max);
        r.setRecordedAt(at(d, 12, 0));
        r.setSource(source);
        com.dnd.smartflow.qhse.web.EnvironmentalReadingController.evaluate(r);
        return r;
    }

    // =============================================================== integrations & notifications
    private void seedIntegrations() {
        Object[][] defs = {{"SAP S/4HANA — ERP Groupe Atlas (DEMO / SIMULATED)", "SIM_ERP", "SAP", List.of("PRODUCT", "CUSTOMER", "SUPPLIER", "STOCK"), 60},
                {"Odoo 17 — Filiale distribution Oran (DEMO / SIMULATED)", "SIM_ERP", "ODOO", List.of("CUSTOMER", "STOCK"), 120},
                {"SAP PM — GMAO usines (DEMO / SIMULATED)", "SIM_CMMS", null, List.of("EQUIPMENT", "WORK_ORDER"), 60},
                {"Suivi GPS de la flotte (DEMO / SIMULATED)", "SIM_GPS", null, List.of("VEHICLE_POSITION"), 5},
                {"Capteurs IoT température / humidité (DEMO / SIMULATED)", "SIM_IOT", null, List.of("SENSOR_READING"), 15}};
        for (Object[] d : defs) {
            IntegrationConnector c = new IntegrationConnector();
            c.setName((String) d[0]);
            c.setProvider((String) d[1]);
            c.setCategory(switch ((String) d[1]) {
                case "SIM_ERP" -> "ERP";
                case "SIM_CMMS" -> "CMMS";
                case "SIM_GPS" -> "GPS";
                default -> "IOT";
            });
            c.setMode("SIMULATED");
            c.setBaseUrl("simulated://" + ((String) d[1]).toLowerCase(Locale.ROOT));
            c.setAuthType("NONE");
            Map<String, Object> cfg = new LinkedHashMap<>();
            if (d[2] != null) cfg.put("flavour", d[2]);
            c.setConfig(cfg);
            @SuppressWarnings("unchecked")
            List<String> ents = (List<String>) d[3];
            c.setEntityTypes(new ArrayList<>(ents));
            c.setEnabled(true);
            c.setSyncFrequencyMinutes((Integer) d[4]);
            c.setConflictPolicy("REMOTE_WINS");
            c.setWebhookSecretEncrypted(null);
            connectors.save(c);
        }
    }

    private void runInitialSyncs() {
        for (IntegrationConnector c : connectors.findAll()) {
            try {
                syncEngine.run(c.getId(), "SCHEDULED");
            } catch (Exception e) {
                log.warn("Initial demo sync of {} failed: {}", c.getName(), e.getMessage());
            }
        }
    }

    private void seedNotifications() {
        Object[][] defs = {{"QHSE_DIRECTOR", "Accident grave — ligne couches L7", "Un opérateur a été blessé à la main. Investigation à lancer.", "CRITICAL", "SAFETY"},
                {"QUALITY_MANAGER", "NC critique : corps étranger métallique", "Réclamation client sur Coquillettes 500 g — lot bloqué automatiquement.", "CRITICAL", "QUALITY"},
                {"QHSE_DIRECTOR", "Pic de non-conformités sur la ligne L2", "4 défauts de soudure en une semaine (moyenne 0,6).", "WARNING", "QUALITY"},
                {"LOGISTICS_MANAGER", "46 livraisons planifiées aujourd'hui", "Optimisez les tournées des dépôts Alger, Constantine et Oran.", "INFO", "DISTRIBUTION"},
                {"LOGISTICS_MANAGER", "Risque de rupture : Couscous moyen 1 kg", "Couverture de stock inférieure à 7 jours.", "WARNING", "INVENTORY"},
                {"SAFETY_MANAGER", "Permis de travail actif aujourd'hui", "Vérifiez les permis en cours sur le site de Constantine.", "INFO", "SAFETY"},
                {"HYGIENE_MANAGER", "Activité de nuisibles — entrepôt Oran", "Le prestataire signale une consommation d'appâts côté quai.", "WARNING", "HYGIENE"},
                {"ENVIRONMENT_MANAGER", "pH eaux usées hors limites", "Valeur 9,2 relevée en sortie NEP — Constantine.", "WARNING", "ENVIRONMENT"},
                {"GENERAL_MANAGER", "Audit de certification ISO 45001 dans 38 jours", "Préparation et revue documentaire à planifier.", "INFO", "AUDIT"},
                {"EMPLOYEE", "Action CAPA à réaliser", "Vous êtes responsable d'actions correctives sur la ligne L2.", "INFO", "CAPA"}};
        int minutes = 5;
        for (Object[] d : defs) {
            Notification n = new Notification();
            n.setTargetRole((String) d[0]);
            n.setTitle((String) d[1]);
            n.setMessage((String) d[2]);
            n.setSeverity((String) d[3]);
            n.setCategory((String) d[4]);
            notifications.save(n);
            minutes += 37;
        }
        for (Object[] d : defs) {
            if (!"QHSE_DIRECTOR".equals(d[0])) continue;
            Notification n = new Notification();
            n.setTargetRole("SUPER_ADMIN");
            n.setTitle((String) d[1]);
            n.setMessage((String) d[2]);
            n.setSeverity((String) d[3]);
            n.setCategory((String) d[4]);
            notifications.save(n);
        }
    }
}
