package org.institutonata.suprihub.contract.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Item do Anexo XIII confirmado à mão (o que o sistema não tem como saber sozinho). */
@Entity
@IdClass(DossierCheck.Key.class)
@Table(name = "dossier_check")
public class DossierCheck {

    public static class Key implements Serializable {
        private Long serviceOrderId;
        private short itemNo;

        public Key() {
        }

        public Key(Long serviceOrderId, short itemNo) {
            this.serviceOrderId = serviceOrderId;
            this.itemNo = itemNo;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(serviceOrderId, k.serviceOrderId) && itemNo == k.itemNo;
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceOrderId, itemNo);
        }
    }

    @Id
    @Column(name = "service_order_id")
    private Long serviceOrderId;
    @Id
    @Column(name = "item_no")
    private short itemNo;
    @Column(nullable = false)
    private boolean checked;
    private String note;
    @Column(name = "checked_by", nullable = false)
    private Long checkedBy;
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected DossierCheck() {
    }

    public DossierCheck(Long serviceOrderId, int itemNo) {
        this.serviceOrderId = serviceOrderId;
        this.itemNo = (short) itemNo;
    }

    public void mark(boolean checked, String note, Long userId, Instant now) {
        this.checked = checked;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.checkedBy = userId;
        this.checkedAt = now;
    }

    public Long getServiceOrderId() {
        return serviceOrderId;
    }

    public int getItemNo() {
        return itemNo;
    }

    public boolean isChecked() {
        return checked;
    }

    public String getNote() {
        return note;
    }

    public Long getCheckedBy() {
        return checkedBy;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
