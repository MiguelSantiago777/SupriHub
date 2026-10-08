-- Troca de e-mail pela tela "Minha conta": o link vai para o novo endereço e só depois de
-- confirmado ele substitui o e-mail de acesso. Os endereços ficam no banco (não no payload
-- dos eventos, que não carrega dado pessoal): o pedido em pending_email, o anterior em
-- previous_email (para avisar o titular) e o do link no próprio token.
ALTER TABLE app_user ADD COLUMN pending_email VARCHAR(254);
ALTER TABLE app_user ADD COLUMN previous_email VARCHAR(254);
ALTER TABLE email_verification_token ADD COLUMN new_email VARCHAR(254);
