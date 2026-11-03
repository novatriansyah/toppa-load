import React, { useState, useMemo } from 'react';
import { TaskSummary } from '../ProjectTypes';

interface TaskCalendarViewProps {
  tasks: TaskSummary[];
  onTaskClick: (task: TaskSummary) => void;
}

export const TaskCalendarView: React.FC<TaskCalendarViewProps> = ({
  tasks,
  onTaskClick,
}) => {
  const [currentDate, setCurrentDate] = useState(() => new Date());

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();

  const monthName = useMemo(() => {
    return currentDate.toLocaleDateString('id-ID', { month: 'long', year: 'numeric' });
  }, [currentDate]);

  const shiftMonth = (direction: -1 | 1) => {
    const next = new Date(currentDate);
    next.setMonth(next.getMonth() + direction);
    setCurrentDate(next);
  };

  // Calendar cells generation (Mon .. Sun)
  const calendarDays = useMemo(() => {
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);

    // Day of week for 1st: 0=Sun, 1=Mon, ..., 6=Sat
    let firstDOW = firstDay.getDay();
    firstDOW = firstDOW === 0 ? 6 : firstDOW - 1; // 0=Mon .. 6=Sun

    const days: { dateStr: string; dayNum: number; isCurrentMonth: boolean }[] = [];

    // Preceding padding
    for (let i = firstDOW - 1; i >= 0; i--) {
      const d = new Date(year, month, -i);
      days.push({
        dateStr: d.toISOString().split('T')[0],
        dayNum: d.getDate(),
        isCurrentMonth: false,
      });
    }

    // Current month days
    for (let i = 1; i <= lastDay.getDate(); i++) {
      const d = new Date(year, month, i);
      days.push({
        dateStr: d.toISOString().split('T')[0],
        dayNum: i,
        isCurrentMonth: true,
      });
    }

    // Trailing padding to complete 35 or 42 grid cells
    const remaining = 7 - (days.length % 7);
    if (remaining < 7) {
      for (let i = 1; i <= remaining; i++) {
        const d = new Date(year, month + 1, i);
        days.push({
          dateStr: d.toISOString().split('T')[0],
          dayNum: d.getDate(),
          isCurrentMonth: false,
        });
      }
    }

    return days;
  }, [year, month]);

  // Index tasks by date
  const tasksByDate = useMemo(() => {
    const map = new Map<string, TaskSummary[]>();
    tasks.forEach((t) => {
      const targetDate = t.dueDate || t.startDate;
      if (targetDate) {
        if (!map.has(targetDate)) {
          map.set(targetDate, []);
        }
        map.get(targetDate)!.push(t);
      }
    });
    return map;
  }, [tasks]);

  const DAY_HEADERS = ['Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu', 'Minggu'];

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-3 space-y-3 shadow-xs">
      {/* Navigator */}
      <div className="flex items-center justify-between pb-2 border-b border-slate-200">
        <h3 className="text-xs font-semibold text-slate-800 capitalize">{monthName}</h3>
        <div className="flex items-center space-x-1">
          <button
            type="button"
            onClick={() => shiftMonth(-1)}
            className="px-2 py-0.5 rounded border border-slate-300 text-xs hover:bg-slate-50 cursor-pointer font-mono"
          >
            ‹
          </button>
          <button
            type="button"
            onClick={() => setCurrentDate(new Date())}
            className="px-2 py-0.5 rounded border border-slate-300 text-[11px] hover:bg-slate-50 cursor-pointer font-medium"
          >
            Bulan Ini
          </button>
          <button
            type="button"
            onClick={() => shiftMonth(1)}
            className="px-2 py-0.5 rounded border border-slate-300 text-xs hover:bg-slate-50 cursor-pointer font-mono"
          >
            ›
          </button>
        </div>
      </div>

      {/* Calendar Grid */}
      <div className="grid grid-cols-7 gap-px bg-slate-200 border border-slate-200 rounded overflow-hidden text-xs">
        {/* Header */}
        {DAY_HEADERS.map((h, i) => (
          <div
            key={h}
            className={`p-1.5 text-center font-semibold text-[11px] bg-slate-50 text-slate-600 ${
              i >= 5 ? 'text-slate-400' : ''
            }`}
          >
            {h}
          </div>
        ))}

        {/* Days */}
        {calendarDays.map((day) => {
          const dayTasks = tasksByDate.get(day.dateStr) || [];
          return (
            <div
              key={day.dateStr}
              className={`min-h-[72px] p-1.5 flex flex-col justify-between ${
                day.isCurrentMonth ? 'bg-white' : 'bg-slate-50/70 text-slate-400'
              }`}
            >
              <div className="flex justify-between items-center mb-1">
                <span className="font-mono text-[11px] font-medium">{day.dayNum}</span>
                {dayTasks.length > 0 && (
                  <span className="text-[9px] font-mono px-1 rounded-full bg-blue-100 text-blue-800 font-bold">
                    {dayTasks.length}
                  </span>
                )}
              </div>

              {/* Tasks preview chips */}
              <div className="space-y-1 overflow-y-auto max-h-14">
                {dayTasks.map((t) => (
                  <div
                    key={t.id}
                    onClick={() => onTaskClick(t)}
                    className="truncate px-1 py-0.5 rounded bg-blue-50 hover:bg-blue-100 text-blue-900 text-[10px] border border-blue-200 cursor-pointer font-medium"
                    title={`${t.title} (${t.status})`}
                  >
                    {t.title}
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

