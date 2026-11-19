package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.Certification;
import com.dnd.smartflow.qhse.domain.CertificationRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/certifications")
@Tag(name = "Training & certifications")
public class CertificationController extends CrudController<Certification> {

    public CertificationController(CrudSupport support, CertificationRepository repository) {
        super(support, repository, Certification.class, "training");
    }

    public static String statusFor(LocalDate expiry, String current) {
        if ("SUSPENDED".equals(current)) return current;
        if (expiry == null) return "VALID";
        LocalDate today = LocalDate.now();
        if (expiry.isBefore(today)) return "EXPIRED";
        if (expiry.isBefore(today.plusDays(60))) return "EXPIRING";
        return "VALID";
    }

    @Override
    protected void beforeSave(Certification c) {
        c.setStatus(statusFor(c.getExpiryDate(), c.getStatus()));
    }
}
