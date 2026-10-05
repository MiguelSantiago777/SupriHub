package org.institutonata.suprihub.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base dos testes de integração: PostgreSQL 16 e RabbitMQ reais (Testcontainers, um par
 * por JVM). O envio de e-mail é interceptado para inspecionar as mensagens.
 * Jobs agendados ficam desligados; o relay do outbox é acionado explicitamente.
 */
@SpringBootTest(properties = {
        "suprihub.scheduling.enabled=false",
        "suprihub.rate-limit.enabled=false",
        "suprihub.jwt.secret=test-secret-0123456789abcdef0123456789abcdef",
        "suprihub.jwt.secure-cookie=false",
        "suprihub.bootstrap.admin-email=admin@teste.local",
        "suprihub.bootstrap.admin-password=Admin@12345",
        "suprihub.bootstrap.demo-users=false",
        "suprihub.storage.local-path=target/test-files",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine");

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected JavaMailSender mailSender;

    protected final List<MimeMessage> sentMails = new CopyOnWriteArrayList<>();

    @BeforeEach
    void captureMail() {
        sentMails.clear();
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        doAnswer(inv -> {
            sentMails.add(inv.getArgument(0));
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
    }
}
