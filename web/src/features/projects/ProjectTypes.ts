export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'BLOCKED';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface ProjectSummary {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  status: ProjectStatus | string;
  startDate: string;
  endDate: string;
  ownerId?: string | null;
  ownerName?: string | null;
  taskCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface TaskSummary {
  id: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  title: string;
  description?: string | null;
  status: TaskStatus | string;
  priority: TaskPriority | string;
  estimatedHours: number;
  startDate?: string | null;
  dueDate?: string | null;
  progressPercent: number;
  assigneeId?: string | null;
  assigneeName?: string | null;
  labels?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateProjectRequest {
  code: string;
  name: string;
  description?: string | null;
  status?: string;
  startDate: string;
  endDate: string;
  ownerId?: string | null;
}

export interface UpdateProjectRequest {
  code?: string;
  name?: string;
  description?: string | null;
  status?: string;
  startDate?: string;
  endDate?: string;
  ownerId?: string | null;
}

export interface CreateTaskRequest {
  title: string;
  description?: string | null;
  status?: string;
  priority?: string;
  estimatedHours?: number;
  startDate?: string | null;
  dueDate?: string | null;
  progressPercent?: number;
  assigneeId?: string | null;
  labels?: string[];
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string | null;
  status?: string;
  priority?: string;
  estimatedHours?: number;
  startDate?: string | null;
  dueDate?: string | null;
  progressPercent?: number;
  assigneeId?: string | null;
  labels?: string[];
}
