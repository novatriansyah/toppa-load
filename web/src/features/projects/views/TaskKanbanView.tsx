import React from 'react';
import { TaskSummary, TaskStatus } from '../ProjectTypes';

interface TaskKanbanViewProps {
  tasks: TaskSummary[];
  onTaskClick: (task: TaskSummary) => void;
  onStatusChange: (taskId: string, newStatus: TaskStatus) => void;
}

const COLUMNS: { status: TaskStatus; label: string; headerColor: string; bg: string }[] = [
  { status: 'TODO', label: 'To Do', headerColor: 'text-slate-700 border-slate-300', bg: 'bg-slate-50/70' },
  { status: 'IN_PROGRESS', label: 'Sedang Dikerjakan', headerColor: 'text-blue-700 border-blue-300', bg: 'bg-blue-50/30' },
  { status: 'BLOCKED', label: 'Tertunda / Terblokir', headerColor: 'text-rose-700 border-rose-300', bg: 'bg-rose-50/30' },
  { status: 'DONE', label: 'Selesai', headerColor: 'text-emerald-700 border-emerald-300', bg: 'bg-emerald-50/30' },
];

export const TaskKanbanView: React.FC<TaskKanbanViewProps> = ({
  tasks,
  onTaskClick,
  onStatusChange,
}) => {
  const getPriorityBadge = (p: string) => {
    switch (p) {
      case 'URGENT':
        return 'bg-rose-100 text-rose-800 border-rose-200';
      case 'HIGH':
        return 'bg-amber-100 text-amber-800 border-amber-200';
      case 'LOW':
        return 'bg-slate-100 text-slate-600 border-slate-200';
      default:
        return 'bg-blue-100 text-blue-700 border-blue-200';
    }
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
      {COLUMNS.map((col) => {
        const colTasks = tasks.filter((t) => t.status === col.status);
        return (
          <div
            key={col.status}
            className={`flex flex-col rounded-lg border border-slate-200 ${col.bg} p-2.5 min-h-[380px] max-h-[600px] shadow-xs`}
          >
            {/* Column Header */}
            <div className="flex items-center justify-between pb-2 border-b border-slate-200 mb-2.5">
              <span className={`text-xs font-semibold ${col.headerColor}`}>
                {col.label}
              </span>
              <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-white border border-slate-200 text-slate-600 font-bold">
                {colTasks.length}
              </span>
            </div>

            {/* Task Cards Container */}
            <div className="flex-1 overflow-y-auto space-y-2 pr-0.5">
              {colTasks.length === 0 ? (
                <div className="h-28 flex items-center justify-center text-slate-400 text-[11px] italic">
                  Kosong
                </div>
              ) : (
                colTasks.map((task) => (
                  <div
                    key={task.id}
                    className="bg-white rounded-md border border-slate-200 p-2.5 shadow-xs hover:border-slate-300 transition-all cursor-pointer space-y-2"
                    onClick={() => onTaskClick(task)}
                  >
                    {/* Priority & Quick Move */}
                    <div className="flex items-center justify-between">
                      <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${getPriorityBadge(task.priority)}`}>
                        {task.priority}
                      </span>
                      <select
                        value={task.status}
                        onClick={(e) => e.stopPropagation()}
                        onChange={(e) => {
                          e.stopPropagation();
                          onStatusChange(task.id, e.target.value as TaskStatus);
                        }}
                        className="text-[10px] font-medium border border-slate-200 rounded px-1 py-0.5 bg-slate-50 text-slate-600 focus:outline-none"
                      >
                        <option value="TODO">To Do</option>
                        <option value="IN_PROGRESS">In Progress</option>
                        <option value="BLOCKED">Blocked</option>
                        <option value="DONE">Done</option>
                      </select>
                    </div>

                    {/* Task Title */}
                    <h4 className="text-xs font-semibold text-slate-800 leading-snug">
                      {task.title}
                    </h4>

                    {/* Progress Bar */}
                    <div className="space-y-1">
                      <div className="flex justify-between text-[10px] text-slate-500 font-mono">
                        <span>Progress</span>
                        <span>{task.progressPercent}%</span>
                      </div>
                      <div className="h-1.5 w-full bg-slate-100 rounded-full overflow-hidden">
                        <div
                          className={`h-full transition-all ${
                            task.progressPercent === 100
                              ? 'bg-emerald-500'
                              : task.progressPercent > 50
                              ? 'bg-blue-500'
                              : 'bg-amber-500'
                          }`}
                          style={{ width: `${task.progressPercent}%` }}
                        />
                      </div>
                    </div>

                    {/* Footer Info: Estimated hours, Due date, Assignee */}
                    <div className="flex items-center justify-between pt-1 border-t border-slate-100 text-[10px] text-slate-500">
                      <span className="font-mono">
                        {task.estimatedHours != null ? `${task.estimatedHours}j` : '-'}
                      </span>
                      {task.assigneeName && (
                        <span className="truncate max-w-[100px] font-medium text-slate-700 bg-slate-100 px-1.5 py-0.2 rounded">
                          {task.assigneeName}
                        </span>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};

