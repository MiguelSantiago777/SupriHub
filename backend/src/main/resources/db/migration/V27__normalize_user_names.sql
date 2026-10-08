-- Nomes já cadastrados todo em minúsculas ou todo em maiúsculas passam a ter iniciais
-- maiúsculas, com "da", "de", "do", "das", "dos" e "e" em minúsculas (mesma regra de PersonName).
UPDATE app_user
SET full_name = regexp_replace(regexp_replace(regexp_replace(regexp_replace(regexp_replace(
                regexp_replace(regexp_replace(regexp_replace(
                    initcap(lower(btrim(regexp_replace(full_name, '\s+', ' ', 'g')))),
                ' Da ', ' da ', 'g'), ' Das ', ' das ', 'g'), ' De ', ' de ', 'g'), ' Di ', ' di ', 'g'),
                ' Do ', ' do ', 'g'), ' Dos ', ' dos ', 'g'), ' Du ', ' du ', 'g'), ' E ', ' e ', 'g'),
    updated_at = now()
WHERE full_name = lower(full_name) OR full_name = upper(full_name);
