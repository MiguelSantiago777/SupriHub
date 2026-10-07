package org.institutonata.suprihub.contract.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.contract.api.OrderDtos.InvoiceRequest;
import org.institutonata.suprihub.contract.api.OrderDtos.OrderDetail;
import org.institutonata.suprihub.contract.api.OrderDtos.OrderSummary;
import org.institutonata.suprihub.contract.api.OrderDtos.PaymentRequest;
import org.institutonata.suprihub.contract.api.OrderDtos.ReasonRequest;
import org.institutonata.suprihub.contract.api.OrderDtos.ReceiptRequest;
import org.institutonata.suprihub.contract.api.OrderDtos.TermsRequest;
import org.institutonata.suprihub.contract.api.OrderDtos.VersionRequest;
import org.institutonata.suprihub.contract.application.OrderPdf;
import org.institutonata.suprihub.contract.application.ServiceOrderService;
import org.institutonata.suprihub.contract.application.ServiceOrderService.Invoice;
import org.institutonata.suprihub.contract.application.ServiceOrderService.View;
import org.institutonata.suprihub.contract.domain.ContractEnums.FileKind;
import org.institutonata.suprihub.contract.domain.ContractEnums.Status;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.contract.domain.ServiceOrderFile;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.domain.Tender;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ordens de Fornecimento/Serviço para a equipe. Leitura: todos os perfis internos. Preparar e
 * emitir: Comissão e gestor da demanda. Receber: gestor e Comissão. Nota e pagamento: Financeiro.
 */
@RestController
@RequestMapping("/api/contracts")
@PreAuthorize("hasAnyRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN')")
@Tag(name = "Contratação (equipe)")
public class ServiceOrderController {

    private static final String PREPARE = "hasAnyRole('COMMISSION', 'DEMAND_MANAGER', 'ADMIN')";
    private static final String FINANCE = "hasAnyRole('FINANCE', 'ADMIN')";

    private final ServiceOrderService orders;
    private final OrderPdf pdf;
    private final CalendarService calendar;

    public ServiceOrderController(ServiceOrderService orders, OrderPdf pdf, CalendarService calendar) {
        this.orders = orders;
        this.pdf = pdf;
        this.calendar = calendar;
    }

    @GetMapping
    @Operation(summary = "Ordens por situação")
    public PageResponse<OrderSummary> list(@RequestParam(defaultValue = "DRAFT") List<Status> status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(orders.search(status, PageRequest.of(page, Math.min(size, 100),
                Sort.by("updatedAt").descending())), this::summary);
    }

    @GetMapping("/counts")
    public Map<Status, Long> counts() {
        return orders.counts();
    }

    @GetMapping("/by-tender/{tenderId}")
    public OrderSummary byTender(@PathVariable long tenderId) {
        return summary(orders.byTender(tenderId).orElseThrow(() -> new NotFoundException("Ordem de fornecimento")));
    }

    @GetMapping("/{id}")
    public OrderDetail get(@PathVariable long id) {
        return detail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(PREPARE)
    @Operation(summary = "Completa a ordem em preparação (projeto, prazos, pagamento, sinal, multa)")
    public OrderDetail update(@PathVariable long id, @Valid @RequestBody TermsRequest req) {
        orders.update(id, req.toTerms(), req.version());
        return detail(id);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize(PREPARE)
    @Operation(summary = "Emite (ou reemite) a ordem; Faixa 1: o aceite já vale como assinatura")
    public OrderDetail issue(@PathVariable long id, @RequestBody VersionRequest req) {
        orders.issue(id, CurrentUser.requireId(), req.version());
        return detail(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(PREPARE)
    public OrderDetail cancel(@PathVariable long id, @Valid @RequestBody ReasonRequest req) {
        orders.cancel(id, req.reason(), req.version());
        return detail(id);
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize(PREPARE)
    @Operation(summary = "Termo de Recebimento e Avaliação (Anexo IX)")
    public OrderDetail receive(@PathVariable long id, @Valid @RequestBody ReceiptRequest req) {
        orders.receive(id, req.toData(), CurrentUser.requireId());
        return detail(id);
    }

    @PostMapping(path = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anexa evidência (gestor), nota fiscal ou comprovante de pagamento (Financeiro)")
    @PreAuthorize("hasAnyRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'ADMIN')")
    public OrderDetail upload(@PathVariable long id, @RequestParam FileKind kind,
            @RequestPart("file") MultipartFile file) {
        orders.addFile(id, kind, file, CurrentUser.requireId());
        return detail(id);
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<InputStreamResource> file(@PathVariable long id, @PathVariable long fileId) {
        return download(orders, orders.file(id, fileId));
    }

    @PostMapping(path = "/{id}/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(FINANCE)
    @Operation(summary = "Registra a nota fiscal/RPA recebida")
    public OrderDetail invoice(@PathVariable long id, @RequestPart("data") InvoiceRequest req,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        orders.invoice(id, new Invoice(req.number(), req.date(), req.value()), file, CurrentUser.requireId(), null);
        return detail(id);
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize(FINANCE)
    @Operation(summary = "Registra o pagamento (exige comprovante anexado)")
    public OrderDetail pay(@PathVariable long id, @RequestBody PaymentRequest req) {
        orders.pay(id, req.paidOn(), req.value(), req.method(), CurrentUser.requireId(), req.version());
        return detail(id);
    }

    @PostMapping("/{id}/upfront-payment")
    @PreAuthorize(FINANCE)
    @Operation(summary = "Registra o pagamento do sinal (Edital 10.4; exige comprovante do sinal)")
    public OrderDetail payUpfront(@PathVariable long id, @RequestBody PaymentRequest req) {
        orders.payUpfront(id, req.paidOn(), req.value(), req.method(), CurrentUser.requireId(), req.version());
        return detail(id);
    }

    @PostMapping("/{id}/call-next")
    @PreAuthorize(PREPARE)
    @Operation(summary = "Ordem não assinada: cancela e convoca o próximo classificado (Edital 9.1)")
    public OrderDetail callNext(@PathVariable long id, @RequestBody VersionRequest req) {
        return detail(orders.callNext(id, CurrentUser.requireId(), req.version()).getId());
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "A ordem em PDF (Anexo VIII)")
    public ResponseEntity<byte[]> pdf(@PathVariable long id) {
        View v = orders.view(id);
        return pdfResponse(v, pdf);
    }

    static ResponseEntity<byte[]> pdfResponse(View v, OrderPdf pdf) {
        ServiceOrder o = v.order();
        String name = (o.getNumber() == null ? "minuta-ordem-" + o.getId() : o.getNumber()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(pdf.render(v));
    }

    static ResponseEntity<InputStreamResource> download(ServiceOrderService orders, ServiceOrderFile f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getFileType()))
                .contentLength(f.getFileSize())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(orders.open(f)));
    }

    private OrderDetail detail(long id) {
        return OrderDetail.of(orders.view(id), calendar.today());
    }

    private OrderSummary summary(ServiceOrder o) {
        View v = orders.view(o.getId());
        Tender t = v.tender();
        SupplierApplication s = v.supplier();
        return new OrderSummary(o.getId(), o.getNumber(), o.getStatus(), o.getStatus().label(), o.getKind().label(),
                t.getId(), t.getNumber(), t.getTitle(), s == null ? null : OrderDtos.name(s), o.getTotalValue(),
                o.getSignatureDueDate(), o.getPaymentDueDate(), o.getRating(), o.getUpdatedAt());
    }
}
