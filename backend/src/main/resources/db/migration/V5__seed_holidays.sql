-- Dias não úteis 2026–2027: feriados nacionais, estaduais (RJ) e municipais (Mesquita, sede do Instituto).
-- optional_point = ponto facultativo; contam como não úteis enquanto o parâmetro
-- optional_points_are_non_business = true. O ADMIN mantém esta tabela pela tela de feriados.
-- ATENÇÃO: conferir o calendário municipal de Mesquita a cada ano (ver docs/DECISOES.md).
INSERT INTO business_holiday (holiday_date, name, scope, optional_point) VALUES
-- 2026
('2026-01-01', 'Confraternização Universal', 'NACIONAL', FALSE),
('2026-02-16', 'Carnaval (segunda-feira)', 'NACIONAL', TRUE),
('2026-02-17', 'Carnaval (terça-feira)', 'NACIONAL', TRUE),
('2026-02-18', 'Quarta-feira de Cinzas (até 14h)', 'NACIONAL', TRUE),
('2026-04-03', 'Paixão de Cristo', 'NACIONAL', FALSE),
('2026-04-21', 'Tiradentes', 'NACIONAL', FALSE),
('2026-05-01', 'Dia do Trabalho', 'NACIONAL', FALSE),
('2026-06-04', 'Corpus Christi', 'NACIONAL', TRUE),
('2026-09-07', 'Independência do Brasil', 'NACIONAL', FALSE),
('2026-10-12', 'Nossa Senhora Aparecida', 'NACIONAL', FALSE),
('2026-11-02', 'Finados', 'NACIONAL', FALSE),
('2026-11-15', 'Proclamação da República', 'NACIONAL', FALSE),
('2026-11-20', 'Dia Nacional de Zumbi e da Consciência Negra', 'NACIONAL', FALSE),
('2026-12-25', 'Natal', 'NACIONAL', FALSE),
('2026-04-23', 'Dia de São Jorge', 'ESTADUAL', FALSE),
-- 2027
('2027-01-01', 'Confraternização Universal', 'NACIONAL', FALSE),
('2027-02-08', 'Carnaval (segunda-feira)', 'NACIONAL', TRUE),
('2027-02-09', 'Carnaval (terça-feira)', 'NACIONAL', TRUE),
('2027-02-10', 'Quarta-feira de Cinzas (até 14h)', 'NACIONAL', TRUE),
('2027-03-26', 'Paixão de Cristo', 'NACIONAL', FALSE),
('2027-04-21', 'Tiradentes', 'NACIONAL', FALSE),
('2027-05-01', 'Dia do Trabalho', 'NACIONAL', FALSE),
('2027-05-27', 'Corpus Christi', 'NACIONAL', TRUE),
('2027-09-07', 'Independência do Brasil', 'NACIONAL', FALSE),
('2027-10-12', 'Nossa Senhora Aparecida', 'NACIONAL', FALSE),
('2027-11-02', 'Finados', 'NACIONAL', FALSE),
('2027-11-15', 'Proclamação da República', 'NACIONAL', FALSE),
('2027-11-20', 'Dia Nacional de Zumbi e da Consciência Negra', 'NACIONAL', FALSE),
('2027-12-25', 'Natal', 'NACIONAL', FALSE),
('2027-04-23', 'Dia de São Jorge', 'ESTADUAL', FALSE);

-- Mesquita: aniversário de emancipação (25/09).
INSERT INTO business_holiday (holiday_date, name, scope, optional_point, municipality_id)
SELECT d::date, 'Aniversário de Mesquita', 'MUNICIPAL', FALSE, m.id
FROM municipality m, (VALUES ('2026-09-25'), ('2027-09-25')) AS v(d)
WHERE m.ibge_code = '3302858';
