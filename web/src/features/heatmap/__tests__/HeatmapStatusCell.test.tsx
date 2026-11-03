import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import i18n from '../../../i18n';
import { HeatmapStatusCell, CapacityCellData } from '../HeatmapStatusCell';

describe('HeatmapStatusCell accessibility and formatting', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('id');
  });

  it('renders overloaded cell with percentage, ratio, warning label, and leave conflict badge', () => {
    const cell: CapacityCellData = {
      bucketStart: '2026-11-02',
      bucketEnd: '2026-11-08',
      netCapacityHours: 32,
      leaveHours: 8,
      confirmedHours: 40,
      tentativeHours: 0,
      utilizationPct: 125.0,
      status: 'OVERLOADED',
      hasLeaveConflict: true,
    };

    render(<HeatmapStatusCell cell={cell} />);

    // Tabular percentage
    expect(screen.getByText('125.0%')).toBeDefined();
    // Hours ratio
    expect(screen.getByText(/40(\.0)?\s*\/\s*32(\.0)?j/i)).toBeDefined();
    // Accessible indicator / badge
    expect(screen.getByLabelText(/Kelebihan Beban/i)).toBeDefined();
    // Leave conflict text
    expect(screen.getByText(/Konflik Cuti/i)).toBeDefined();
  });

  it('renders zero net capacity with confirmed hours as conflict text without NaN% or Infinity%', () => {
    const cell: CapacityCellData = {
      bucketStart: '2026-11-02',
      bucketEnd: '2026-11-08',
      netCapacityHours: 0,
      leaveHours: 40,
      confirmedHours: 8,
      tentativeHours: 0,
      utilizationPct: null,
      status: 'OVERLOADED',
      hasLeaveConflict: true,
    };

    const { container } = render(<HeatmapStatusCell cell={cell} />);

    expect(container.textContent).not.toContain('NaN');
    expect(container.textContent).not.toContain('Infinity');
    expect(screen.getByText(/8(\.0)?\s*\/\s*0(\.0)?j/i)).toBeDefined();
    expect(screen.getByText(/Konflik Cuti/i)).toBeDefined();
  });

  it('renders tentative hours indicator when tentativeHours > 0', () => {
    const cell: CapacityCellData = {
      bucketStart: '2026-11-02',
      bucketEnd: '2026-11-08',
      netCapacityHours: 40,
      leaveHours: 0,
      confirmedHours: 30,
      tentativeHours: 20,
      utilizationPct: 75.0,
      status: 'OPTIMAL',
      hasLeaveConflict: false,
    };

    render(<HeatmapStatusCell cell={cell} />);

    expect(screen.getByText('75.0%')).toBeDefined();
    expect(screen.getByText(/\+20(\.0)?j\s*tentatif/i)).toBeDefined();
  });
});

