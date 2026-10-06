package org.institutonata.suprihub.contract.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.contract.api.OrderDtos.InvoiceRequest;
import org.institutonata.suprihub.contract.api.OrderDtos.OrderDetail;
import org.institutonata.suprihub.contract.api.OrderDtos.OrderSummary;
import org.institutonata.suprihub.contract.api.OrderDtos.SignRequest;
import org.institutonata.suprihub.contract.application.OrderPdf;
import org.institutonata.suprihub.contract.application.ServiceOrderService;
import org.institutonata.suprihub.contract.application.ServiceOrderService.Invoice;
import org.institutonata.suprihub.contract.application.ServiceOrderService.View;
import org.institutonata.suprihub.contract.domain.ContractEnums.FileKind;
import org.institutonata.suprihub.contract.domain.ServiceOrderFile;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.application.OpportunityService;
import org.institutonata.suprihub.tender.domain.Tender;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** "Meus contratos" do fornecedor: assinar a ordem, ver o termo de recebimento, enviar a nota. */
@RestController
@RequestMapping("/api/supplier/contracts")
@PreAuthorize("hasRole('SUPPLIER')")
@Tag(name = "Contratação (fornecedor)")
public class SupplierOrderController {

    private final ServiceOrderService orders;
    private final OpportunityService opportunities;
    private final OrderPdf pdf;
    private final CalendarService calendar;

    public SupplierOrderController(ServiceOrderService orders, OpportunityService opportunities, OrderPdf pdf,
            CalendarService calendar) {
        this.orders = orders;
        this.opportunities = opportunities;
        this.pdf = pdf;
        this.calendar = calendar;
    }

    @GetMapping
    public List<OrderSummary> list() {
        long app = applicationId();
        return orders.forSupplier(app).stream().map(o -> {
            View v = orders.viewForSupplier(app, o.getId());
            Tender t = v.tender();
            return new OrderSummary(o.getId(), o.getNumber(), o.getStatus(), o.getStatus().label(),
                    o.getKind().label(), t.getId(), t.getNumber(), t.getTitle(), null, o.getTotalValue(),
                    o.getSignatureDueDate(), o.getPaymentDueDate(), o.getRating(), o.getUpdatedAt());
        }).toList();
    }

    @GetMapping("/{id}")
    public OrderDetail get(@PathVariable long id) {
        return detail(id);
    }

    @PostMapping("/{id}/sign")
    @Operation(summary = "Assina eletronicamente a ordem (Edital 9.1)")
    public OrderDetail sign(@PathVariable long id, @RequestBody SignRequest req, HttpServletRequest http) {
        orders.sign(applicationId(), id, CurrentUser.requireId(), http.getRemoteAddr(), req.declarationAccepted());
        return detail(id);
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "Dá ciência do Termo de Recebimento e Avaliação")
    public OrderDetail acknowledge(@PathVariable long id) {
        orders.acknowledge(applicationId(), id);
        return detail(id);
    }

    @PostMapping(path = "/{id}/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia a nota fiscal ou o RPA (depois do recebimento definitivo)")
    public OrderDetail invoice(@PathVariable long id, @RequestPart("data") InvoiceRequest req,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        orders.invoice(id, new Invoice(req.number(), req.date(), req.value()), file, CurrentUser.requireId(),
                applicationId());
        return detail(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long id) {
        return ServiceOrderController.pdfResponse(orders.viewForSupplier(applicationId(), id), pdf);
    }

    /** O fornecedor baixa a própria nota e as evidências; o comprovante de pagamento também. */
    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<InputStreamResource> file(@PathVariable long id, @PathVariable long fileId) {
        orders.viewForSupplier(applicationId(), id);
        ServiceOrderFile f = orders.file(id, fileId);
        if (f.getKind() != FileKind.INVOICE && f.getKind() != FileKind.PAYMENT_PROOF
                && f.getKind() != FileKind.EVIDENCE) {
            throw new NotFoundException("Arquivo");
        }
        return ServiceOrderController.download(orders, f);
    }

    private long applicationId() {
        SupplierApplication app = opportunities.application(CurrentUser.requireId());
        return app.getId();
    }

    private OrderDetail detail(long id) {
        View v = orders.viewForSupplier(applicationId(), id);
        return OrderDetail.of(v, calendar.today());
    }
}
