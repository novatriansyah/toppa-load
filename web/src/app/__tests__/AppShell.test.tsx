import { render, screen, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, it, expect, beforeEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import fs from 'node:fs';
import path from 'node:path';
import { AppShell } from '../AppShell';
import idLocale from '../../i18n/locales/id.json';
import enLocale from '../../i18n/locales/en.json';
import i18n from '../../i18n';

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      {ui}
    </QueryClientProvider>
  );
}

function flattenKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(obj)
    .flatMap(([key, value]) => {
      const fullKey = prefix ? `${prefix}.${key}` : key;
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        return flattenKeys(value as Record<string, unknown>, fullKey);
      }
      return [fullKey];
    })
    .sort();
}

describe('AppShell & Localization Parity', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('id');
  });

  it('renders primary work navigation in Indonesian from translation files', () => {
    renderWithProviders(
      <MemoryRouter initialEntries={['/beban-kerja']}>
        <AppShell />
      </MemoryRouter>
    );
    const nav = screen.getByRole('navigation');
    expect(nav).toHaveTextContent('Beban Kerja');
    expect(nav).toHaveTextContent('Proyek & Task');
    expect(nav).toHaveTextContent('Timesheet');
    expect(nav).toHaveTextContent('Alokasi');
    expect(nav).toHaveTextContent('Laporan');
    expect(nav).toHaveTextContent('Simulasi');
    expect(nav).toHaveTextContent('Cuti & Libur');
    expect(nav).toHaveTextContent('Pengaturan');
  });

  it('renders distinct work navigation links with expected paths', () => {
    renderWithProviders(
      <MemoryRouter initialEntries={['/beban-kerja']}>
        <AppShell />
      </MemoryRouter>
    );
    const nav = screen.getByRole('navigation');
    const links = nav.querySelectorAll('a');
    expect(links.length).toBeGreaterThanOrEqual(6);

    const hrefs = Array.from(links).map((link) => link.getAttribute('href'));
    expect(hrefs).toContain('/beban-kerja');
    expect(hrefs).toContain('/proyek');
    expect(hrefs).toContain('/timesheet');
    expect(hrefs).toContain('/alokasi');
    expect(hrefs).toContain('/laporan');
    expect(hrefs).toContain('/simulasi');
    expect(hrefs).toContain('/cuti');
    expect(hrefs).toContain('/pengaturan');
  });

  it('keeps identical top-level and nested translation keys between id.json and en.json', () => {
    expect(flattenKeys(idLocale)).toEqual(flattenKeys(enLocale));
  });

  it('updates navigation labels when switching language to English', async () => {
    const { rerender } = renderWithProviders(
      <MemoryRouter initialEntries={['/beban-kerja']}>
        <AppShell />
      </MemoryRouter>
    );

    await act(async () => {
      await i18n.changeLanguage('en');
    });

    rerender(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/beban-kerja']}>
          <AppShell />
        </MemoryRouter>
      </QueryClientProvider>
    );

    const nav = screen.getByRole('navigation');
    expect(nav).toHaveTextContent('Workload');
    expect(nav).toHaveTextContent('Projects & Tasks');
    expect(nav).toHaveTextContent('Allocations');
    expect(nav).toHaveTextContent('Leaves & Holidays');
    expect(nav).toHaveTextContent('Settings');
  });

  it('defines work-tool tokens and utility classes in tokens.css', () => {
    const tokensPath = path.resolve(__dirname, '../../styles/tokens.css');
    const tokensContent = fs.readFileSync(tokensPath, 'utf-8');

    expect(tokensContent).toContain('.num-tabular');
    expect(tokensContent).toContain('font-variant-numeric: tabular-nums');
    expect(tokensContent).toContain('text-align: right');
    expect(tokensContent).toContain('.cell-overloaded-pattern');
    expect(tokensContent).toContain('--space-1: 4px');
    expect(tokensContent).toContain('--radius-sm: 4px');
    expect(tokensContent).toContain('--radius-md: 6px');
    expect(tokensContent).toContain('--border-width: 1px');
  });
});
