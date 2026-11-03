$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Login as Admin
Write-Host "Logging in as admin..."
$loginBody = @{ email = 'admin@toppaload.local'; password = 'admin123' } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -WebSession $session | Out-Null

$xsrfCookie = $session.Cookies.GetCookies((New-Object System.Uri($baseUrl))) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }
$headers = @{ 'X-XSRF-TOKEN' = $xsrfCookie.Value }

# 2. Get Users map
$users = Invoke-RestMethod -Uri "$baseUrl/api/v1/users" -Method Get -WebSession $session
$userMap = @{}
foreach ($u in $users) {
    $userMap[$u.email] = $u
    Write-Host "Found user: $($u.fullName) ($($u.email)) -> $($u.id)"
}

$masato = $userMap['masato.nakai@steelmaking.corp']
$takashi = $userMap['takashi.kawano@steelmaking.corp']
$nova = $userMap['nova.triansyah@steelmaking.corp']
$ali = $userMap['ali.ramdani@steelmaking.corp']
$alvian = $userMap['alvian.eugene@steelmaking.corp']
$iqbal = $userMap['iqbal.tawaqal@steelmaking.corp']

# 3. Seed Skills for all members
Write-Host "Seeding User Skills..."
$skillsData = @{
    $masato.id = @(
        @{ skillName = 'Project Management'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Steel Plant Domain'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Architecture Review'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Jira / Scrum'; proficiencyLevel = 'ADVANCED' }
    )
    $takashi.id = @(
        @{ skillName = 'Code Review'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Industrial Automation'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Safety Compliance'; proficiencyLevel = 'ADVANCED' },
        @{ skillName = 'Database Design'; proficiencyLevel = 'ADVANCED' }
    )
    $nova.id = @(
        @{ skillName = 'Fullstack Lead'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'React / TypeScript'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Spring Boot'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Docker & CI/CD'; proficiencyLevel = 'ADVANCED' },
        @{ skillName = 'System Architecture'; proficiencyLevel = 'ADVANCED' }
    )
    $ali.id = @(
        @{ skillName = 'Java / Spring Boot'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Microservices'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'PostgreSQL Optimization'; proficiencyLevel = 'ADVANCED' },
        @{ skillName = 'SCADA OPC-UA'; proficiencyLevel = 'INTERMEDIATE' }
    )
    $alvian.id = @(
        @{ skillName = 'React / TypeScript'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Tailwind CSS'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'UI/UX Design'; proficiencyLevel = 'ADVANCED' },
        @{ skillName = 'Vite & Frontend Tooling'; proficiencyLevel = 'ADVANCED' }
    )
    $iqbal.id = @(
        @{ skillName = 'PLC / SCADA Integration'; proficiencyLevel = 'EXPERT' },
        @{ skillName = 'Data Pipeline & Kafka'; proficiencyLevel = 'ADVANCED' },
        @{ skillName = 'Python Data Analytics'; proficiencyLevel = 'ADVANCED' },
        @{ skillName = 'Java / Spring Boot'; proficiencyLevel = 'INTERMEDIATE' }
    )
}

foreach ($userId in $skillsData.Keys) {
    $body = @{ skills = $skillsData[$userId] } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/v1/users/$userId/skills" -Method Put -ContentType 'application/json' -Body $body -Headers $headers -WebSession $session | Out-Null
}
Write-Host "User skills seeded successfully."

# 4. Check existing projects & Seed 4 Projects
Write-Host "Seeding 4 Projects across 2026..."
$existingProjects = Invoke-RestMethod -Uri "$baseUrl/api/v1/projects" -Method Get -WebSession $session
$projectMap = @{}
foreach ($p in $existingProjects) {
    $projectMap[$p.code] = $p
}

$projectsToCreate = @(
    @{
        code = 'BFMS-2026';
        name = 'Blast Furnace Monitoring System';
        description = 'Real-time thermal distribution & pressure sensor telemetry for Blast Furnace #2, replacing legacy SCADA screens with responsive web dashboards.';
        status = 'COMPLETED';
        startDate = '2026-01-05';
        endDate = '2026-06-30';
        ownerId = $masato.id
    },
    @{
        code = 'LMF-QA';
        name = 'LMF Quality Analytics & Heat Tracking';
        description = 'Chemical composition tracking & heating curve analytics during secondary refining in Ladle Metallurgy Furnaces to reduce reheat cycle variance.';
        status = 'COMPLETED';
        startDate = '2026-04-01';
        endDate = '2026-09-30';
        ownerId = $takashi.id
    },
    @{
        code = 'CCM-SLAB';
        name = 'Continuous Casting Slab Tracking System';
        description = 'Real-time slab cutting, spray cooling monitoring, and laser dimensional inspection integration for the continuous casting machine strand #3.';
        status = 'ACTIVE';
        startDate = '2026-07-01';
        endDate = '2026-12-31';
        ownerId = $masato.id
    },
    @{
        code = 'STEEL-APP';
        name = 'Steelmaking Mobile Inspector & Downtime App';
        description = 'Mobile PWA for plant floor inspectors to log refractory wear, ladle lining erosion, emergency downtime reasons, and safety hazard reports.';
        status = 'ACTIVE';
        startDate = '2026-09-01';
        endDate = '2027-02-28';
        ownerId = $takashi.id
    }
)

foreach ($proj in $projectsToCreate) {
    if (-not $projectMap.ContainsKey($proj.code)) {
        $pJson = $proj | ConvertTo-Json
        $created = Invoke-RestMethod -Uri "$baseUrl/api/v1/projects" -Method Post -ContentType 'application/json' -Body $pJson -Headers $headers -WebSession $session
        $projectMap[$proj.code] = $created
        Write-Host "Created project: $($created.code) - $($created.name)"
    } else {
        Write-Host "Project $($proj.code) already exists."
    }
}

$bfms = $projectMap['BFMS-2026']
$lmf = $projectMap['LMF-QA']
$ccm = $projectMap['CCM-SLAB']
$stapp = $projectMap['STEEL-APP']

# 5. Seed Tasks for Active Projects
Write-Host "Seeding Tasks for CCM-SLAB and STEEL-APP..."
$tasksCcm = @(
    @{
        title = 'Optical laser slab cut-off length tracking';
        description = 'High precision laser sensor integration to log continuous strand cutting timestamps and dimensions.';
        status = 'IN_PROGRESS';
        priority = 'HIGH';
        estimatedHours = 40.0;
        startDate = '2026-11-02';
        dueDate = '2026-11-20';
        progressPercent = 70;
        assigneeId = $ali.id;
        labels = @('Casting', 'Laser', 'Hardware')
    },
    @{
        title = 'Secondary cooling zone water flow optimization';
        description = 'Hydraulic nozzle pressure monitoring and predictive spray adjustment based on steel heat grade.';
        status = 'IN_PROGRESS';
        priority = 'MEDIUM';
        estimatedHours = 35.0;
        startDate = '2026-11-02';
        dueDate = '2026-11-25';
        progressPercent = 45;
        assigneeId = $iqbal.id;
        labels = @('Cooling', 'SCADA', 'Automation')
    },
    @{
        title = 'Slab inventory & yard positioning 3D grid UI';
        description = 'Interactive warehouse grid visualization showing slab cooling stacks, grade tags, and crane pickup routes.';
        status = 'IN_PROGRESS';
        priority = 'MEDIUM';
        estimatedHours = 50.0;
        startDate = '2026-11-09';
        dueDate = '2026-11-30';
        progressPercent = 60;
        assigneeId = $alvian.id;
        labels = @('Frontend', 'ThreeJS', 'Inventory')
    },
    @{
        title = 'Steel mill ERP integration & heat certificate generation';
        description = 'Automated dispatch certificate PDF generation and SAP S/4HANA production order posting.';
        status = 'TODO';
        priority = 'HIGH';
        estimatedHours = 30.0;
        startDate = '2026-11-16';
        dueDate = '2026-12-05';
        progressPercent = 0;
        assigneeId = $nova.id;
        labels = @('ERP', 'SAP', 'Backend')
    },
    @{
        title = 'Slab surface defect classification service';
        description = 'Computer vision inference service for longitudinal cracking detection on hot slab surfaces.';
        status = 'IN_PROGRESS';
        priority = 'URGENT';
        estimatedHours = 45.0;
        startDate = '2026-11-02';
        dueDate = '2026-11-27';
        progressPercent = 30;
        assigneeId = $ali.id;
        labels = @('AI', 'Quality', 'Critical')
    }
)

$createdCcmTasks = @()
foreach ($t in $tasksCcm) {
    $tJson = $t | ConvertTo-Json
    $created = Invoke-RestMethod -Uri "$baseUrl/api/v1/projects/$($ccm.id)/tasks" -Method Post -ContentType 'application/json' -Body $tJson -Headers $headers -WebSession $session
    $createdCcmTasks += $created
    Write-Host "Created task on CCM-SLAB: $($created.title)"
}

$tasksStapp = @(
    @{
        title = 'Offline-first refractory thickness inspection checklist';
        description = 'Mobile checklist with indexedDB local cache for refractory brick wear measurement on torpedo cars.';
        status = 'IN_PROGRESS';
        priority = 'HIGH';
        estimatedHours = 40.0;
        startDate = '2026-11-02';
        dueDate = '2026-11-20';
        progressPercent = 55;
        assigneeId = $alvian.id;
        labels = @('PWA', 'Offline', 'Mobile')
    },
    @{
        title = 'Unscheduled stoppage & downtime root-cause logger';
        description = 'Operator downtime incident reporting with photos, category codes, and shift handover notes.';
        status = 'IN_PROGRESS';
        priority = 'MEDIUM';
        estimatedHours = 35.0;
        startDate = '2026-11-09';
        dueDate = '2026-11-27';
        progressPercent = 50;
        assigneeId = $nova.id;
        labels = @('Mobile', 'Downtime', 'Fullstack')
    },
    @{
        title = 'Plant emergency push notification via Teams Webhook';
        description = 'Critical stoppage broadcast to Microsoft Teams engineering channel with quick acknowledge buttons.';
        status = 'TODO';
        priority = 'URGENT';
        estimatedHours = 25.0;
        startDate = '2026-11-16';
        dueDate = '2026-11-30';
        progressPercent = 0;
        assigneeId = $ali.id;
        labels = @('Teams', 'Webhook', 'Alert')
    },
    @{
        title = 'SCADA telemetry bridge for mobile quick-glance';
        description = 'Lightweight websocket microservice feeding real-time furnace temperature gauges to mobile devices.';
        status = 'IN_PROGRESS';
        priority = 'MEDIUM';
        estimatedHours = 30.0;
        startDate = '2026-11-09';
        dueDate = '2026-11-27';
        progressPercent = 20;
        assigneeId = $iqbal.id;
        labels = @('Websocket', 'SCADA', 'Telemetry')
    }
)

$createdStappTasks = @()
foreach ($t in $tasksStapp) {
    $tJson = $t | ConvertTo-Json
    $created = Invoke-RestMethod -Uri "$baseUrl/api/v1/projects/$($stapp.id)/tasks" -Method Post -ContentType 'application/json' -Body $tJson -Headers $headers -WebSession $session
    $createdStappTasks += $created
    Write-Host "Created task on STEEL-APP: $($created.title)"
}

# 6. Seed Allocations for November 2026
Write-Host "Seeding Allocations for November 2026 (Creating Overloaded Ali & Underutilized Iqbal)..."
$existingAllocs = Invoke-RestMethod -Uri "$baseUrl/api/v1/allocations?from=2026-11-01&to=2026-11-30" -Method Get -WebSession $session
foreach ($ea in $existingAllocs) {
    Invoke-RestMethod -Uri "$baseUrl/api/v1/allocations/$($ea.id)" -Method Delete -Headers $headers -WebSession $session | Out-Null
}
$allocationsToCreate = @(
    # Ali Ramdani: 4.5h on CCM-T1 + 3.0h on CCM-T5 + 2.0h on STAPP-T3 = 9.5h/day -> 118.75% OVERLOADED!
    @{
        userId = $ali.id;
        projectId = $ccm.id;
        taskId = $createdCcmTasks[0].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 4.50;
        allocationType = 'CONFIRMED';
        notes = 'Primary laser cutting length tracking implementation.'
    },
    @{
        userId = $ali.id;
        projectId = $ccm.id;
        taskId = $createdCcmTasks[4].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 3.00;
        allocationType = 'CONFIRMED';
        notes = 'Surface defect classification inference pipeline.'
    },
    @{
        userId = $ali.id;
        projectId = $stapp.id;
        taskId = $createdStappTasks[2].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 2.00;
        allocationType = 'CONFIRMED';
        notes = 'Emergency Teams webhook integration.'
    },

    # Mochamad Iqbal Tawaqal: 2.5h on CCM-T2 + 1.5h on STAPP-T4 = 4.0h/day -> 50% UNDERUTILIZED (AI transfer target!)
    @{
        userId = $iqbal.id;
        projectId = $ccm.id;
        taskId = $createdCcmTasks[1].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 2.50;
        allocationType = 'CONFIRMED';
        notes = 'Secondary cooling zone telemetry integration.'
    },
    @{
        userId = $iqbal.id;
        projectId = $stapp.id;
        taskId = $createdStappTasks[3].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 1.50;
        allocationType = 'CONFIRMED';
        notes = 'Mobile SCADA telemetry bridge.'
    },

    # Alvian Eugene: 4.0h on CCM-T3 + 3.0h on STAPP-T1 = 7.0h/day -> 87.5% OPTIMAL
    @{
        userId = $alvian.id;
        projectId = $ccm.id;
        taskId = $createdCcmTasks[2].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 4.00;
        allocationType = 'CONFIRMED';
        notes = 'Slab yard 3D visualization grid UI.'
    },
    @{
        userId = $alvian.id;
        projectId = $stapp.id;
        taskId = $createdStappTasks[0].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 3.00;
        allocationType = 'CONFIRMED';
        notes = 'Offline refractory inspection checklist.'
    },

    # Nova Triansyah Azis: 4.0h on CCM-T4 + 4.0h on STAPP-T2 = 8.0h/day -> 100% OPTIMAL
    @{
        userId = $nova.id;
        projectId = $ccm.id;
        taskId = $createdCcmTasks[3].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 4.00;
        allocationType = 'CONFIRMED';
        notes = 'ERP integration & heat certificate PDF generation.'
    },
    @{
        userId = $nova.id;
        projectId = $stapp.id;
        taskId = $createdStappTasks[1].id;
        startDate = '2026-11-01';
        endDate = '2026-11-30';
        hoursPerDay = 4.00;
        allocationType = 'CONFIRMED';
        notes = 'Mobile stoppage incident root-cause logger.'
    }
)

foreach ($a in $allocationsToCreate) {
    $aJson = $a | ConvertTo-Json
    $created = Invoke-RestMethod -Uri "$baseUrl/api/v1/allocations" -Method Post -ContentType 'application/json' -Body $aJson -Headers $headers -WebSession $session
    Write-Host "Created allocation for user $($a.userId) on project $($a.projectId): $($a.hoursPerDay) hrs/day"
}

# 7. Seed Task Comments dengan Penulis Otentik
Write-Host "Seeding Task Comments as Masato Nakai & Ali Ramdani..."
# Delete old comments if created under admin
$existingComments = Invoke-RestMethod -Uri "$baseUrl/api/v1/tasks/$($createdCcmTasks[0].id)/comments" -Method Get -WebSession $session
foreach ($ec in $existingComments) {
    if ($ec.authorEmail -eq 'admin@toppaload.local') {
        Invoke-RestMethod -Uri "$baseUrl/api/v1/tasks/$($createdCcmTasks[0].id)/comments/$($ec.id)" -Method Delete -Headers $headers -WebSession $session | Out-Null
    }
}

# Comment 1: Masato Nakai
$sessionMasato = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginMasato = @{ email = 'masato.nakai@steelmaking.corp'; password = 'password123' } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $loginMasato -WebSession $sessionMasato | Out-Null
$xsrfMasato = $sessionMasato.Cookies.GetCookies((New-Object System.Uri($baseUrl))) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }
$headersMasato = @{ 'X-XSRF-TOKEN' = $xsrfMasato.Value }

$comment1 = @{
    commentText = "Mohon pastikan kalibrasi sensor optik laser diatur pada toleransi +-2mm untuk slab grade SS400."
} | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/api/v1/tasks/$($createdCcmTasks[0].id)/comments" -Method Post -ContentType 'application/json' -Body $comment1 -Headers $headersMasato -WebSession $sessionMasato | Out-Null
Write-Host "Posted comment 1 as Masato Nakai"

# Comment 2: Ali Ramdani
$sessionAli = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginAli = @{ email = 'ali.ramdani@steelmaking.corp'; password = 'password123' } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $loginAli -WebSession $sessionAli | Out-Null
$xsrfAli = $sessionAli.Cookies.GetCookies((New-Object System.Uri($baseUrl))) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }
$headersAli = @{ 'X-XSRF-TOKEN' = $xsrfAli.Value }

$comment2 = @{
    commentText = "Siap Pak Nakai, kami sudah memasang filter kalibrasi di edge device dan pengujian awal menunjukkan akurasi +-1.2mm."
} | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/api/v1/tasks/$($createdCcmTasks[0].id)/comments" -Method Post -ContentType 'application/json' -Body $comment2 -Headers $headersAli -WebSession $sessionAli | Out-Null
Write-Host "Posted comment 2 as Ali Ramdani"

Write-Host "ALL STEELMAKING APPDEV DEMO DATA SEEDED SUCCESSFULLY!"

