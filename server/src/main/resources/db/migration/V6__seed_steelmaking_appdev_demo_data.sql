-- V6__seed_steelmaking_appdev_demo_data.sql
-- Seed data realistis untuk divisi Steelmaking AppDev:
-- Reviewer / Supervisor: Masato Nakai, Takashi Kawano
-- Admin / Lead: Nova Triansyah Azis
-- Anggota: Ali Ramdani (Senior), Alvian Eugene, Mochamad Iqbal Tawaqal
-- 4 Proyek sepanjang tahun 2026, Task, Heatmap Workload (Overload & Underutilized), Skill Matrix, Cuti, dan Timesheet.

-- 1. Pastikan ekstensi pgcrypto tersedia untuk hashing kata sandi
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Seed Pengguna Steelmaking AppDev (Kata sandi default: 'password123')
INSERT INTO users (id, email, full_name, role, work_hours_per_day, work_days, active, email_notifications, password_hash)
VALUES
    ('49819c42-33bf-4842-bb3c-3936de052f4f', 'masato.nakai@steelmaking.corp', 'Masato Nakai', 'SUPERVISOR', 8.00, '{1,2,3,4,5}', TRUE, TRUE, crypt('password123', gen_salt('bf', 10))),
    ('f66c66ff-d44f-490d-a3c8-742019bfecaf', 'takashi.kawano@steelmaking.corp', 'Takashi Kawano', 'SUPERVISOR', 8.00, '{1,2,3,4,5}', TRUE, TRUE, crypt('password123', gen_salt('bf', 10))),
    ('5770dbd7-a164-497e-8adc-bb62776a7270', 'nova.triansyah@steelmaking.corp', 'Nova Triansyah Azis', 'ADMIN', 8.00, '{1,2,3,4,5}', TRUE, TRUE, crypt('password123', gen_salt('bf', 10))),
    ('ee623b45-88f3-4cb1-af66-94ec67641c82', 'ali.ramdani@steelmaking.corp', 'Ali Ramdani (Senior)', 'MEMBER', 8.00, '{1,2,3,4,5}', TRUE, TRUE, crypt('password123', gen_salt('bf', 10))),
    ('49b6bb08-414a-4b91-bfa0-06cd95e7a164', 'alvian.eugene@steelmaking.corp', 'Alvian Eugene', 'MEMBER', 8.00, '{1,2,3,4,5}', TRUE, TRUE, crypt('password123', gen_salt('bf', 10))),
    ('144caf8d-bd37-4a69-83d1-eb95046a77da', 'iqbal.tawaqal@steelmaking.corp', 'Mochamad Iqbal Tawaqal', 'MEMBER', 8.00, '{1,2,3,4,5}', TRUE, TRUE, crypt('password123', gen_salt('bf', 10)))
ON CONFLICT (id) DO UPDATE
SET full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    work_hours_per_day = EXCLUDED.work_hours_per_day,
    work_days = EXCLUDED.work_days,
    active = TRUE,
    password_hash = COALESCE(users.password_hash, EXCLUDED.password_hash);

-- 3. Seed Saldo Cuti 2026 (leave_balances)
INSERT INTO leave_balances (user_id, year, total_allowance_days, used_days, remaining_days)
SELECT id, 2026, 12.0, 0.0, 12.0
FROM users
WHERE email IN (
    'masato.nakai@steelmaking.corp',
    'takashi.kawano@steelmaking.corp',
    'nova.triansyah@steelmaking.corp',
    'ali.ramdani@steelmaking.corp',
    'alvian.eugene@steelmaking.corp',
    'iqbal.tawaqal@steelmaking.corp'
)
ON CONFLICT (user_id, year) DO NOTHING;

-- 4. Seed User Skills (Skill Matrix)
INSERT INTO user_skills (user_id, skill_name, proficiency_level)
VALUES
    -- Masato Nakai
    ('49819c42-33bf-4842-bb3c-3936de052f4f', 'Project Management', 'EXPERT'),
    ('49819c42-33bf-4842-bb3c-3936de052f4f', 'Steel Plant Domain', 'EXPERT'),
    ('49819c42-33bf-4842-bb3c-3936de052f4f', 'Architecture Review', 'EXPERT'),
    ('49819c42-33bf-4842-bb3c-3936de052f4f', 'Jira / Scrum', 'ADVANCED'),
    -- Takashi Kawano
    ('f66c66ff-d44f-490d-a3c8-742019bfecaf', 'Code Review', 'EXPERT'),
    ('f66c66ff-d44f-490d-a3c8-742019bfecaf', 'Industrial Automation', 'EXPERT'),
    ('f66c66ff-d44f-490d-a3c8-742019bfecaf', 'Safety Compliance', 'ADVANCED'),
    ('f66c66ff-d44f-490d-a3c8-742019bfecaf', 'Database Design', 'ADVANCED'),
    -- Nova Triansyah Azis
    ('5770dbd7-a164-497e-8adc-bb62776a7270', 'Fullstack Lead', 'EXPERT'),
    ('5770dbd7-a164-497e-8adc-bb62776a7270', 'React / TypeScript', 'EXPERT'),
    ('5770dbd7-a164-497e-8adc-bb62776a7270', 'Spring Boot', 'EXPERT'),
    ('5770dbd7-a164-497e-8adc-bb62776a7270', 'Docker & CI/CD', 'ADVANCED'),
    ('5770dbd7-a164-497e-8adc-bb62776a7270', 'System Architecture', 'ADVANCED'),
    -- Ali Ramdani
    ('ee623b45-88f3-4cb1-af66-94ec67641c82', 'Java / Spring Boot', 'EXPERT'),
    ('ee623b45-88f3-4cb1-af66-94ec67641c82', 'Microservices', 'EXPERT'),
    ('ee623b45-88f3-4cb1-af66-94ec67641c82', 'PostgreSQL Optimization', 'ADVANCED'),
    ('ee623b45-88f3-4cb1-af66-94ec67641c82', 'SCADA OPC-UA', 'INTERMEDIATE'),
    -- Alvian Eugene
    ('49b6bb08-414a-4b91-bfa0-06cd95e7a164', 'React / TypeScript', 'EXPERT'),
    ('49b6bb08-414a-4b91-bfa0-06cd95e7a164', 'Tailwind CSS', 'EXPERT'),
    ('49b6bb08-414a-4b91-bfa0-06cd95e7a164', 'UI/UX Design', 'ADVANCED'),
    ('49b6bb08-414a-4b91-bfa0-06cd95e7a164', 'Vite & Frontend Tooling', 'ADVANCED'),
    -- Mochamad Iqbal Tawaqal
    ('144caf8d-bd37-4a69-83d1-eb95046a77da', 'PLC / SCADA Integration', 'EXPERT'),
    ('144caf8d-bd37-4a69-83d1-eb95046a77da', 'Data Pipeline & Kafka', 'ADVANCED'),
    ('144caf8d-bd37-4a69-83d1-eb95046a77da', 'Python Data Analytics', 'ADVANCED'),
    ('144caf8d-bd37-4a69-83d1-eb95046a77da', 'Java / Spring Boot', 'INTERMEDIATE')
ON CONFLICT (user_id, skill_name) DO UPDATE
SET proficiency_level = EXCLUDED.proficiency_level;

-- 5. Seed 4 Proyek Sepanjang Tahun 2026
INSERT INTO projects (id, code, name, description, status, start_date, end_date, owner_id)
VALUES
    ('984cb402-bb0c-4448-b4b1-8854f0a06841', 'BFMS-2026', 'Blast Furnace Monitoring System',
     'Real-time thermal distribution & pressure sensor telemetry for Blast Furnace #2, replacing legacy SCADA screens with responsive web dashboards.',
     'COMPLETED', '2026-01-05', '2026-06-30', '49819c42-33bf-4842-bb3c-3936de052f4f'),
    ('68a18355-ce4e-4f76-8fe1-7c9fa991307b', 'LMF-QA', 'LMF Quality Analytics & Heat Tracking',
     'Chemical composition tracking & heating curve analytics during secondary refining in Ladle Metallurgy Furnaces to reduce reheat cycle variance.',
     'COMPLETED', '2026-04-01', '2026-09-30', 'f66c66ff-d44f-490d-a3c8-742019bfecaf'),
    ('11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3', 'CCM-SLAB', 'Continuous Casting Slab Tracking System',
     'Real-time slab cutting, spray cooling monitoring, and laser dimensional inspection integration for the continuous casting machine strand #3.',
     'ACTIVE', '2026-07-01', '2026-12-31', '49819c42-33bf-4842-bb3c-3936de052f4f'),
    ('7fde19c7-cf14-4d10-b77a-438c8111b7c9', 'STEEL-APP', 'Steelmaking Mobile Inspector & Downtime App',
     'Mobile PWA for plant floor inspectors to log refractory wear, ladle lining erosion, emergency downtime reasons, and safety hazard reports.',
     'ACTIVE', '2026-09-01', '2027-02-28', 'f66c66ff-d44f-490d-a3c8-742019bfecaf')
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    owner_id = EXCLUDED.owner_id;

-- 6. Seed Tasks Proyek Aktif
INSERT INTO tasks (id, project_id, title, description, status, priority, estimated_hours, due_date, labels, start_date, progress_percent, assignee_id)
VALUES
    -- CCM-SLAB Tasks
    ('c0000001-0000-0000-0000-000000000001', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3',
     'Optical laser slab cut-off length tracking',
     'High precision laser sensor integration to log continuous strand cutting timestamps and dimensions.',
     'IN_PROGRESS', 'HIGH', 40.0, '2026-11-20', '{"Casting","Laser","Hardware"}', '2026-11-02', 70, 'ee623b45-88f3-4cb1-af66-94ec67641c82'),
    ('c0000001-0000-0000-0000-000000000002', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3',
     'Secondary cooling zone water flow optimization',
     'Hydraulic nozzle pressure monitoring and predictive spray adjustment based on steel heat grade.',
     'IN_PROGRESS', 'MEDIUM', 35.0, '2026-11-25', '{"Cooling","SCADA","Automation"}', '2026-11-02', 45, '144caf8d-bd37-4a69-83d1-eb95046a77da'),
    ('c0000001-0000-0000-0000-000000000003', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3',
     'Slab inventory & yard positioning 3D grid UI',
     'Interactive warehouse grid visualization showing slab cooling stacks, grade tags, and crane pickup routes.',
     'IN_PROGRESS', 'MEDIUM', 50.0, '2026-11-30', '{"Frontend","ThreeJS","Inventory"}', '2026-11-09', 60, '49b6bb08-414a-4b91-bfa0-06cd95e7a164'),
    ('c0000001-0000-0000-0000-000000000004', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3',
     'Steel mill ERP integration & heat certificate generation',
     'Automated dispatch certificate PDF generation and SAP S/4HANA production order posting.',
     'TODO', 'HIGH', 30.0, '2026-12-05', '{"ERP","SAP","Backend"}', '2026-11-16', 0, '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('c0000001-0000-0000-0000-000000000005', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3',
     'Slab surface defect classification service',
     'Computer vision inference service for longitudinal cracking detection on hot slab surfaces.',
     'IN_PROGRESS', 'URGENT', 45.0, '2026-11-27', '{"AI","Quality","Critical"}', '2026-11-02', 30, 'ee623b45-88f3-4cb1-af66-94ec67641c82'),

    -- STEEL-APP Tasks
    ('d0000001-0000-0000-0000-000000000001', '7fde19c7-cf14-4d10-b77a-438c8111b7c9',
     'Offline-first refractory thickness inspection checklist',
     'Mobile checklist with indexedDB local cache for refractory brick wear measurement on torpedo cars.',
     'IN_PROGRESS', 'HIGH', 40.0, '2026-11-20', '{"PWA","Offline","Mobile"}', '2026-11-02', 55, '49b6bb08-414a-4b91-bfa0-06cd95e7a164'),
    ('d0000001-0000-0000-0000-000000000002', '7fde19c7-cf14-4d10-b77a-438c8111b7c9',
     'Unscheduled stoppage & downtime root-cause logger',
     'Operator downtime incident reporting with photos, category codes, and shift handover notes.',
     'IN_PROGRESS', 'MEDIUM', 35.0, '2026-11-27', '{"Mobile","Downtime","Fullstack"}', '2026-11-09', 50, '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('d0000001-0000-0000-0000-000000000003', '7fde19c7-cf14-4d10-b77a-438c8111b7c9',
     'Plant emergency push notification via Teams Webhook',
     'Critical stoppage broadcast to Microsoft Teams engineering channel with quick acknowledge buttons.',
     'TODO', 'URGENT', 25.0, '2026-11-30', '{"Teams","Webhook","Alert"}', '2026-11-16', 0, 'ee623b45-88f3-4cb1-af66-94ec67641c82'),
    ('d0000001-0000-0000-0000-000000000004', '7fde19c7-cf14-4d10-b77a-438c8111b7c9',
     'SCADA telemetry bridge for mobile quick-glance',
     'Lightweight websocket microservice feeding real-time furnace temperature gauges to mobile devices.',
     'IN_PROGRESS', 'MEDIUM', 30.0, '2026-11-27', '{"Websocket","SCADA","Telemetry"}', '2026-11-09', 20, '144caf8d-bd37-4a69-83d1-eb95046a77da')
ON CONFLICT (id) DO NOTHING;

-- 7. Seed Allocations (Bulan Berjalan November 2026)
-- Ali Ramdani: 4.5 + 3.0 + 2.0 = 9.5h/hari (118.8% OVERLOAD)
-- Mochamad Iqbal Tawaqal: 2.5 + 1.5 = 4.0h/hari (50.0% UNDERUTILIZED)
-- Alvian Eugene: 4.0 + 3.0 = 7.0h/hari (87.5% OPTIMAL)
-- Nova Triansyah Azis: 4.0 + 4.0 = 8.0h/hari (100.0% OPTIMAL)
INSERT INTO allocations (id, user_id, project_id, task_id, start_date, end_date, hours_per_day, allocation_type, notes, created_by)
VALUES
    ('a0000001-0000-0000-0000-000000000001', 'ee623b45-88f3-4cb1-af66-94ec67641c82', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3', 'c0000001-0000-0000-0000-000000000001', '2026-11-01', '2026-11-30', 4.50, 'CONFIRMED', 'Primary laser cutting length tracking implementation.', '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('a0000001-0000-0000-0000-000000000002', 'ee623b45-88f3-4cb1-af66-94ec67641c82', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3', 'c0000001-0000-0000-0000-000000000005', '2026-11-01', '2026-11-30', 3.00, 'CONFIRMED', 'Surface defect classification inference pipeline.', '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('a0000001-0000-0000-0000-000000000003', 'ee623b45-88f3-4cb1-af66-94ec67641c82', '7fde19c7-cf14-4d10-b77a-438c8111b7c9', 'd0000001-0000-0000-0000-000000000003', '2026-11-01', '2026-11-30', 2.00, 'CONFIRMED', 'Emergency Teams webhook integration.', '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('a0000001-0000-0000-0000-000000000004', '144caf8d-bd37-4a69-83d1-eb95046a77da', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3', 'c0000001-0000-0000-0000-000000000002', '2026-11-01', '2026-11-30', 2.50, 'CONFIRMED', 'Secondary cooling zone telemetry integration.', '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('a0000001-0000-0000-0000-000000000005', '144caf8d-bd37-4a69-83d1-eb95046a77da', '7fde19c7-cf14-4d10-b77a-438c8111b7c9', 'd0000001-0000-0000-0000-000000000004', '2026-11-01', '2026-11-30', 1.50, 'CONFIRMED', 'Mobile SCADA telemetry bridge.', '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('a0000001-0000-0000-0000-000000000006', '49b6bb08-414a-4b91-bfa0-06cd95e7a164', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3', 'c0000001-0000-0000-0000-000000000003', '2026-11-01', '2026-11-30', 4.00, 'CONFIRMED', 'Slab yard 3D visualization grid UI.', '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('a0000001-0000-0000-0000-000000000007', '49b6bb08-414a-4b91-bfa0-06cd95e7a164', '7fde19c7-cf14-4d10-b77a-438c8111b7c9', 'd0000001-0000-0000-0000-000000000001', '2026-11-01', '2026-11-30', 3.00, 'CONFIRMED', 'Offline refractory inspection checklist.', '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('a0000001-0000-0000-0000-000000000008', '5770dbd7-a164-497e-8adc-bb62776a7270', '11dee4e5-d331-4237-a1a7-7a2c5ef7e4f3', 'c0000001-0000-0000-0000-000000000004', '2026-11-01', '2026-11-30', 4.00, 'CONFIRMED', 'ERP integration & heat certificate PDF generation.', '5770dbd7-a164-497e-8adc-bb62776a7270'),
    ('a0000001-0000-0000-0000-000000000009', '5770dbd7-a164-497e-8adc-bb62776a7270', '7fde19c7-cf14-4d10-b77a-438c8111b7c9', 'd0000001-0000-0000-0000-000000000002', '2026-11-01', '2026-11-30', 4.00, 'CONFIRMED', 'Mobile stoppage incident root-cause logger.', '5770dbd7-a164-497e-8adc-bb62776a7270')
ON CONFLICT (id) DO NOTHING;

-- 8. Seed Task Comments dengan Penulis Otentik (Masato Nakai, Ali Ramdani, Takashi Kawano, Nova Triansyah Azis)
INSERT INTO task_comments (id, task_id, author_id, comment_text, created_at, updated_at)
VALUES
    ('e0000001-0000-0000-0000-000000000001', 'c0000001-0000-0000-0000-000000000001',
     '49819c42-33bf-4842-bb3c-3936de052f4f',
     'Mohon pastikan kalibrasi sensor optik laser diatur pada toleransi +-2mm untuk slab grade SS400.',
     '2026-11-02 09:15:00+07', '2026-11-02 09:15:00+07'),
    ('e0000001-0000-0000-0000-000000000002', 'c0000001-0000-0000-0000-000000000001',
     'ee623b45-88f3-4cb1-af66-94ec67641c82',
     'Siap Pak Nakai, kami sudah memasang filter kalibrasi di edge device dan pengujian awal menunjukkan akurasi +-1.2mm.',
     '2026-11-02 10:30:00+07', '2026-11-02 10:30:00+07'),
    ('e0000001-0000-0000-0000-000000000003', 'd0000001-0000-0000-0000-000000000003',
     'f66c66ff-d44f-490d-a3c8-742019bfecaf',
     'Pastikan payload alert webhook ke channel Teams menggunakan format Adaptive Card dengan tombol Quick Acknowledge.',
     '2026-11-03 08:45:00+07', '2026-11-03 08:45:00+07'),
    ('e0000001-0000-0000-0000-000000000004', 'd0000001-0000-0000-0000-000000000003',
     '5770dbd7-a164-497e-8adc-bb62776a7270',
     'Sudah diintegrasikan dengan modul webhook Teams di backend Toppa Load, tinggal penyesuaian template JSON.',
     '2026-11-03 09:20:00+07', '2026-11-03 09:20:00+07')
ON CONFLICT (id) DO NOTHING;


