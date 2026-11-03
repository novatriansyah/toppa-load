import type { CapacityCellData } from './HeatmapStatusCell';
export type { CapacityCellData };

export type GranularityType = 'DAY' | 'WEEK' | 'MONTH';

export interface UserHeatmapRowData {
  userId: string;
  fullName: string;
  role: string;
  workHoursPerDay: number;
  cells: CapacityCellData[];
}

export interface HeatmapResponseData {
  from: string;
  to: string;
  granularity: GranularityType;
  includeTentative: boolean;
  periods: string[];
  rows: UserHeatmapRowData[];
}

export interface DayBreakdownData {
  date: string;
  dayOfWeek: number;
  isWorkday: boolean;
  isHoliday: boolean;
  holidayName?: string;
  baseHours: number;
  leaveHours: number;
  netCapacityHours: number;
  confirmedHours: number;
  tentativeHours: number;
  status: 'UNDERUTILIZED' | 'OPTIMAL' | 'OVERLOADED' | 'NO_CAPACITY';
  hasLeaveConflict: boolean;
  allocations: AllocationSummaryData[];
  leaves: LeaveSummaryData[];
}

export interface AllocationSummaryData {
  id: string;
  userId: string;
  userName?: string;
  projectId: string;
  projectCode?: string;
  projectName?: string;
  taskId?: string;
  taskTitle?: string;
  startDate: string;
  endDate: string;
  hoursPerDay: number;
  allocationType: 'CONFIRMED' | 'TENTATIVE';
  notes?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface LeaveSummaryData {
  id: string;
  userId: string;
  userEmail?: string;
  fullName?: string;
  leaveType: 'ANNUAL' | 'SICK' | 'UNPAID' | 'OTHER';
  portion: 'FULL' | 'HALF_AM' | 'HALF_PM';
  startDate: string;
  endDate: string;
  status: string;
  notes?: string;
  createdAt: string;
}

export interface HolidaySummaryData {
  id: string;
  holidayDate: string;
  name: string;
  scope: 'NATIONAL' | 'COMPANY';
  createdAt: string;
}

export interface AllocationPreviewResponseData {
  beforeCells: CapacityCellData[];
  afterCells: CapacityCellData[];
  introducesOverload: boolean;
  introducesLeaveConflict: boolean;
}

