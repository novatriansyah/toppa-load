export interface TimesheetSubmission {
  id: string;
  userId: string;
  userFullName: string;
  weekStartDate: string;
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'RETURNED';
  submittedAt: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  reviewerNote: string | null;
}

export interface TimeEntry {
  id: string;
  submissionId: string | null;
  userId: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  taskId: string | null;
  taskTitle: string | null;
  entryDate: string;
  hours: number;
  notes: string | null;
}

export interface TaskWorkloadComparison {
  taskId: string;
  taskTitle: string;
  projectId: string;
  projectCode: string;
  estimatedHours: number;
  allocatedHours: number;
  actualLoggedHours: number;
}

export interface WeeklyTimesheetData {
  submission: TimesheetSubmission;
  entries: TimeEntry[];
  comparisons: TaskWorkloadComparison[];
}

export interface TimeEntryInput {
  id?: string;
  projectId: string;
  taskId?: string;
  entryDate: string;
  hours: number;
  notes?: string;
}

export interface SaveWeeklyTimesheetRequest {
  weekStartDate: string;
  entries: TimeEntryInput[];
}

