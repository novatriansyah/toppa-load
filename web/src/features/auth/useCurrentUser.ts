import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/httpClient';

export type UserRole = 'ADMIN' | 'SUPERVISOR' | 'MEMBER';

export interface AuthenticatedActor {
  userId: string;
  email: string;
  fullName: string;
  role: UserRole;
  workHoursPerDay: number;
  workDays: number[];
}

export function useCurrentUser() {
  return useQuery<AuthenticatedActor>({
    queryKey: ['currentUser'],
    queryFn: () => apiClient.get<AuthenticatedActor>('/api/v1/me'),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}
