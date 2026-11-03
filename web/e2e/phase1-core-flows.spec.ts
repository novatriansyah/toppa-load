import { test, expect } from '@playwright/test';

const mockCurrentUser = {
  userId: '00000000-0000-0000-0000-000000000001',
  email: 'admin@toppaload.local',
  fullName: 'Budi Santoso',
  role: 'ADMIN',
  workHoursPerDay: 8.0,
  workDays: [1, 2, 3, 4, 5],
};

const mockSystemInfo = {
  appName: 'Toppa Load',
  version: 'v0.1.0',
  workTimezone: 'Asia/Jakarta',
  currentWorkDate: '2026-11-02',
};

test.describe('Fase 1 Core E2E Flows', () => {

  test('Flow 1: Login & Access Guard with Dual SSO and PDP Notice', async ({ page }) => {
    // 1. Unauthenticated access redirects to /login
    await page.route('**/api/v1/me', (route) => {
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      });
    });

    await page.goto('/beban-kerja');
    await expect(page).toHaveURL(/.*\/login/);

    // 2. Dual SSO buttons are visible (Google & Microsoft)
    const googleBtn = page.getByRole('link', { name: /Google/i });
    const msBtn = page.getByRole('link', { name: /Microsoft/i });
    await expect(googleBtn).toBeVisible();
    await expect(msBtn).toBeVisible();

    // 3. UU PDP Transparency Notice is visible
    await expect(page.getByText(/UU PDP No\. 27\/2022/i)).toBeVisible();
  });

  test('Flow 1B: Authenticated Shell Navigation and Notification Popover', async ({ page }) => {
    await page.route('**/api/v1/me', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockCurrentUser),
      });
    });

    await page.route('**/api/v1/system/info', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSystemInfo),
      });
    });

    await page.route('**/api/v1/notifications', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              id: 'notif-001',
              type: 'OVERLOAD_WARNING',
              title: 'Peringatan beban kerja melebihi 100%',
              payload: {},
              readAt: null,
              emailStatus: 'SENT',
              createdAt: '2026-11-02T08:00:00Z',
            },
          ],
          unreadCount: 1,
        }),
      });
    });

    await page.route('**/api/v1/capacity/heatmap*', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          from: '2026-11-02',
          to: '2026-11-29',
          granularity: 'WEEK',
          includeTentative: false,
          periods: ['2026-11-02'],
          rows: [],
        }),
      });
    });

    await page.goto('/beban-kerja');
    await expect(page).toHaveURL(/.*\/beban-kerja/);
    await expect(page.getByText('Budi Santoso')).toBeVisible();
    await expect(page.getByText('Toppa Load', { exact: true })).toBeVisible();

    // Notification bell shows badge and opens popover
    const bellBtn = page.getByTestId('notification-bell');
    await expect(bellBtn).toBeVisible();
    await expect(page.getByTestId('unread-badge')).toHaveText('1');

    await bellBtn.click();
    await expect(page.getByTestId('notifications-popover')).toBeVisible();
    await expect(page.getByText('Peringatan beban kerja melebihi 100%')).toBeVisible();
  });

  test('Flow 2: Create Project and Quick-Add Task via Keyboard', async ({ page }) => {
    await page.route('**/api/v1/me', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockCurrentUser),
      });
    });

    await page.route('**/api/v1/system/info', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSystemInfo),
      });
    });

    await page.route('**/api/v1/notifications', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], unreadCount: 0 }),
      });
    });

    const projectsList = [
      {
        id: 'prj-001',
        code: 'PRJ-CORE',
        name: 'Infrastruktur Inti',
        status: 'ACTIVE',
        startDate: '2026-11-01',
        endDate: '2026-12-31',
        ownerName: 'Budi Santoso',
        totalTasks: 1,
      },
    ];

    const tasksList = [
      {
        id: 'tsk-001',
        projectId: 'prj-001',
        title: 'Migrasi Database PostgreSQL 17',
        status: 'TODO',
        priority: 'HIGH',
        progressPercent: 0,
        estimatedHours: 16,
        startDate: '2026-11-02',
        dueDate: '2026-11-06',
        assigneeName: 'Rina Pratama',
      },
    ];

    await page.route('**/api/v1/projects*', (route) => {
      if (route.request().method() === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}');
        const created = {
          id: 'prj-002',
          code: body.code,
          name: body.name,
          status: body.status || 'ACTIVE',
          startDate: body.startDate,
          endDate: body.endDate,
          ownerName: 'Budi Santoso',
          totalTasks: 0,
        };
        projectsList.push(created);
        route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(created),
        });
      } else {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(projectsList),
        });
      }
    });

    await page.route('**/api/v1/projects/prj-001/tasks*', (route) => {
      if (route.request().method() === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}');
        const createdTask = {
          id: 'tsk-002',
          projectId: 'prj-001',
          title: body.title,
          status: body.status || 'TODO',
          priority: body.priority || 'MEDIUM',
          progressPercent: body.progressPercent || 0,
          estimatedHours: body.estimatedHours || 0,
          startDate: body.startDate || null,
          dueDate: body.dueDate || null,
          assigneeName: null,
        };
        tasksList.push(createdTask);
        route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(createdTask),
        });
      } else {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(tasksList),
        });
      }
    });

    await page.route('**/api/v1/users*', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'user-001',
            email: 'rina@toppaload.local',
            fullName: 'Rina Pratama',
            role: 'MEMBER',
            active: true,
            workHoursPerDay: 8.0,
            workDays: [1, 2, 3, 4, 5],
          },
        ]),
      });
    });

    await page.goto('/proyek');
    await expect(page.getByText('Infrastruktur Inti')).toBeVisible();

    // Open Tasks Drawer
    await page.getByRole('button', { name: /Buka Task/i }).first().click();
    await expect(page.getByText('Migrasi Database PostgreSQL 17')).toBeVisible();

    // Quick-add task via keyboard Enter
    const quickInput = page.getByPlaceholder(/Tambah task cepat/i);
    await quickInput.fill('Setup SSL Reverse Proxy');
    await quickInput.press('Enter');

    await expect(page.getByText('Setup SSL Reverse Proxy')).toBeVisible();
  });

  test('Flow 3: Workload Heatmap Displays Overloaded Cell with Accessible Indicator', async ({ page }) => {
    await page.route('**/api/v1/me', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockCurrentUser),
      });
    });

    await page.route('**/api/v1/system/info', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSystemInfo),
      });
    });

    await page.route('**/api/v1/notifications', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], unreadCount: 0 }),
      });
    });

    // Provide heatmap data with 1 OVERLOADED user (125% utilization)
    await page.route('**/api/v1/capacity/heatmap*', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          from: '2026-11-02',
          to: '2026-11-29',
          granularity: 'WEEK',
          includeTentative: false,
          periods: ['2026-11-02'],
          rows: [
            {
              userId: 'user-001',
              fullName: 'Rina Pratama',
              role: 'MEMBER',
              workHoursPerDay: 8.0,
              cells: [
                {
                  bucketStart: '2026-11-02',
                  bucketEnd: '2026-11-08',
                  netCapacityHours: 40.0,
                  leaveHours: 0.0,
                  confirmedHours: 50.0,
                  tentativeHours: 0.0,
                  utilizationPct: 125.0,
                  status: 'OVERLOADED',
                  hasLeaveConflict: false,
                },
              ],
            },
          ],
        }),
      });
    });

    await page.goto('/beban-kerja');
    await expect(page.getByText('Rina Pratama')).toBeVisible();

    // Check for 125.0% utilization text
    const cell = page.getByText(/125\.0%/).first();
    await expect(cell).toBeVisible();

    // Check accessible status indicator for Overloaded
    const statusPill = page.getByText(/Kelebihan Beban/i).first();
    await expect(statusPill).toBeVisible();
  });

  test('Flow 4: Leave Tracking and Leave Conflict Alert', async ({ page }) => {
    await page.route('**/api/v1/me', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockCurrentUser),
      });
    });

    await page.route('**/api/v1/system/info', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSystemInfo),
      });
    });

    await page.route('**/api/v1/notifications', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], unreadCount: 0 }),
      });
    });

    const leavesList = [
      {
        id: 'leave-001',
        userId: 'user-001',
        fullName: 'Rina Pratama',
        leaveType: 'ANNUAL',
        portion: 'FULL',
        startDate: '2026-11-04',
        endDate: '2026-11-04',
        status: 'APPROVED',
        notes: 'Urusan Keluarga',
        createdAt: '2026-11-01T00:00:00Z',
      },
    ];

    await page.route('**/api/v1/leaves*', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(leavesList),
      });
    });

    await page.route('**/api/v1/holidays*', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto('/cuti');
    await expect(page.getByText('Rina Pratama')).toBeVisible();
    await expect(page.getByText('Urusan Keluarga')).toBeVisible();

    // Now navigate to heatmap with leave conflict present
    await page.route('**/api/v1/capacity/heatmap*', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          from: '2026-11-02',
          to: '2026-11-29',
          granularity: 'WEEK',
          includeTentative: false,
          periods: ['2026-11-02'],
          rows: [
            {
              userId: 'user-001',
              fullName: 'Rina Pratama',
              role: 'MEMBER',
              workHoursPerDay: 8.0,
              cells: [
                {
                  bucketStart: '2026-11-02',
                  bucketEnd: '2026-11-08',
                  netCapacityHours: 32.0, // Reduced by 8h leave
                  leaveHours: 8.0,
                  confirmedHours: 40.0,
                  tentativeHours: 0.0,
                  utilizationPct: 125.0,
                  status: 'OVERLOADED',
                  hasLeaveConflict: true, // Conflict detected!
                },
              ],
            },
          ],
        }),
      });
    });

    await page.goto('/beban-kerja');
    await expect(page.getByText(/Konflik Cuti/i).first()).toBeVisible();
  });
});
