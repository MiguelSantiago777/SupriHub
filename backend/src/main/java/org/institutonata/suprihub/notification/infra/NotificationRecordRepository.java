package org.institutonata.suprihub.notification.infra;

import java.util.List;

import org.institutonata.suprihub.notification.domain.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    List<NotificationRecord> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
}
