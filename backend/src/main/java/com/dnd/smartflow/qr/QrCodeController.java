package com.dnd.smartflow.qr;

import com.dnd.smartflow.assets.domain.EquipmentRepository;
import com.dnd.smartflow.catalog.domain.ProductRepository;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.fleet.domain.VehicleRepository;
import com.dnd.smartflow.inventory.domain.BatchRepository;
import com.dnd.smartflow.inventory.domain.WarehouseRepository;
import com.dnd.smartflow.qhse.domain.InspectionRepository;
import com.dnd.smartflow.tenant.Tenant;
import com.dnd.smartflow.tenant.TenantRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.*;

/**
 * QR codes for products, batches, equipment, warehouses, vehicles and inspections. A QR encodes a link to
 * {@code /qr/{type}/{id}}; scanning opens the digital record. When the tenant allows public QR access,
 * a limited public summary is available without login; full records always require authentication.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "QR codes")
public class QrCodeController {
    private final ProductRepository products;
    private final BatchRepository batches;
    private final EquipmentRepository equipment;
    private final WarehouseRepository warehouses;
    private final VehicleRepository vehicles;
    private final InspectionRepository inspections;
    private final TenantRepository tenants;

    @Value("${smartflow.public-url:http://localhost:4200}")
    private String publicUrl;

    @GetMapping(value = "/api/v1/qr/{type}/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "QR code (PNG) pointing to the record's digital page")
    public ResponseEntity<byte[]> qr(@PathVariable String type, @PathVariable UUID id, @RequestParam(defaultValue = "320") int size) {
        resolve(type, id).orElseThrow(() -> ApiException.notFound(type));
        String url = publicUrl.replaceAll("/$", "") + "/qr/" + type + "/" + id + "?t=" + TenantContext.require();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(7))).contentType(MediaType.IMAGE_PNG)
                .body(png(url, Math.max(120, Math.min(1024, size))));
    }

    @GetMapping(value = "/api/v1/public/qr-image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> anyQr(@RequestParam String url, @RequestParam(defaultValue = "320") int size) {
        if (!url.startsWith(publicUrl) && !url.startsWith("http://localhost")) throw ApiException.badRequest("FOREIGN_URL", "Only platform URLs can be encoded");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png(url, Math.max(120, Math.min(1024, size))));
    }

    /** Public, limited view used when scanning a QR without being signed in. */
    @GetMapping("/api/v1/public/qr/{type}/{id}")
    public Map<String, Object> publicView(@PathVariable String type, @PathVariable UUID id, @RequestParam("t") UUID tenantId) {
        Tenant t = tenants.findById(tenantId).orElseThrow(() -> ApiException.notFound("Record"));
        if (!t.getSettings().isPublicQrAccess()) {
            return Map.of("type", type, "id", id, "requiresLogin", true);
        }
        return TenantContext.callAs(tenantId, () -> {
            Map<String, Object> m = new LinkedHashMap<>(resolve(type, id).orElseThrow(() -> ApiException.notFound("Record")));
            m.put("organization", t.getName());
            m.put("requiresLogin", false);
            return m;
        });
    }

    private Optional<Map<String, Object>> resolve(String type, UUID id) {
        return switch (type) {
            case "product" -> products.findById(id).map(p -> Map.<String, Object>of("type", type, "id", id, "title", p.getName(),
                    "fields", fields("SKU", p.getSku(), "Category", p.getCategory(), "Brand", p.getBrand(), "Shelf life (days)", p.getShelfLifeDays())));
            case "batch" -> batches.findById(id).map(b -> Map.<String, Object>of("type", type, "id", id, "title", "Batch " + b.getBatchNumber(),
                    "fields", fields("Status", b.getStatus(), "Quality", b.getQualityStatus(), "Production date", b.getProductionDate(), "Expiry date", b.getExpiryDate()),
                    "route", "/traceability/" + id));
            case "equipment" -> equipment.findById(id).map(e -> Map.<String, Object>of("type", type, "id", id, "title", e.getName(),
                    "fields", fields("Code", e.getCode(), "Status", e.getStatus(), "Next maintenance", e.getNextMaintenanceDate(), "Criticality", e.getCriticality())));
            case "warehouse" -> warehouses.findById(id).map(w -> Map.<String, Object>of("type", type, "id", id, "title", w.getName(),
                    "fields", fields("Code", w.getCode(), "City", w.getCity(), "Temperature controlled", w.getTemperatureControlled())));
            case "vehicle" -> vehicles.findById(id).map(v -> Map.<String, Object>of("type", type, "id", id, "title", v.getPlateNumber(),
                    "fields", fields("Type", v.getType(), "Status", v.getStatus(), "Last hygiene check", v.getLastHygieneCheck())));
            case "inspection" -> inspections.findById(id).map(i -> Map.<String, Object>of("type", type, "id", id, "title", i.getReference() + " — " + i.getTitle(),
                    "fields", fields("Status", i.getStatus(), "Result", i.getResult(), "Score", i.getScore(), "Date", i.getScheduledDate())));
            default -> Optional.empty();
        };
    }

    private static Map<String, Object> fields(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1] == null ? "—" : kv[i + 1]);
        return m;
    }

    static byte[] png(String content, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("QR generation failed", e);
        }
    }
}
