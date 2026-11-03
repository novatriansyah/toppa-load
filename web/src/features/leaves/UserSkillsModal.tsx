import React, { useState, useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';

interface UserSkillEntry {
  skillName: string;
  proficiencyLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
}

interface UserSkillsModalProps {
  isOpen: boolean;
  onClose: () => void;
  userId: string;
  currentSkills: UserSkillEntry[];
}

export const UserSkillsModal: React.FC<UserSkillsModalProps> = ({
  isOpen,
  onClose,
  userId,
  currentSkills,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [skills, setSkills] = useState<UserSkillEntry[]>([]);
  const [newSkillName, setNewSkillName] = useState('');
  const [newLevel, setNewLevel] = useState<'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'>('INTERMEDIATE');

  useEffect(() => {
    if (isOpen) {
      setSkills(currentSkills.map((s) => ({ ...s })));
      setNewSkillName('');
      setNewLevel('INTERMEDIATE');
    }
  }, [isOpen, currentSkills]);

  const updateMutation = useMutation({
    mutationFn: (updated: UserSkillEntry[]) =>
      apiClient.put(`/api/v1/users/${userId}/skills`, { skills: updated }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skillsDirectory'] });
      onClose();
    },
  });

  if (!isOpen) return null;

  const handleAddSkill = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newSkillName.trim();
    if (!trimmed) return;
    if (skills.some((s) => s.skillName.toLowerCase() === trimmed.toLowerCase())) {
      alert(t('skills.duplicateSkill', 'Keahlian ini sudah ada di daftar.'));
      return;
    }
    setSkills([...skills, { skillName: trimmed, proficiencyLevel: newLevel }]);
    setNewSkillName('');
    setNewLevel('INTERMEDIATE');
  };

  const handleRemoveSkill = (index: number) => {
    setSkills(skills.filter((_, i) => i !== index));
  };

  const handleSave = () => {
    updateMutation.mutate(skills);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg border border-slate-200 overflow-hidden flex flex-col max-h-[90vh]">
        <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-bold text-slate-900">
              {t('skills.manageTitle', 'Kelola Keahlian (Skill Matrix)')}
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              {t('skills.manageSubtitle', 'Perbarui daftar kemampuan teknis atau keahlian Anda.')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1 text-sm rounded hover:bg-slate-100"
          >
            ✕
          </button>
        </div>

        <div className="p-5 overflow-y-auto space-y-4">
          <form onSubmit={handleAddSkill} className="flex gap-2">
            <input
              type="text"
              placeholder={t('skills.skillNamePlaceholder', 'Contoh: React 19, Java, PostgreSQL...')}
              value={newSkillName}
              onChange={(e) => setNewSkillName(e.target.value)}
              className="flex-1 px-3 py-1.5 border border-slate-300 rounded text-xs focus:ring-1 focus:ring-slate-900 focus:outline-hidden"
            />
            <select
              value={newLevel}
              onChange={(e) => setNewLevel(e.target.value as any)}
              className="px-2.5 py-1.5 border border-slate-300 rounded text-xs bg-white text-slate-800"
            >
              <option value="BEGINNER">{t('skills.levelBeginner', 'Pemula (Beginner)')}</option>
              <option value="INTERMEDIATE">{t('skills.levelIntermediate', 'Menengah (Intermediate)')}</option>
              <option value="ADVANCED">{t('skills.levelAdvanced', 'Mahir (Advanced)')}</option>
              <option value="EXPERT">{t('skills.levelExpert', 'Ahli (Expert)')}</option>
            </select>
            <button
              type="submit"
              className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded text-xs font-medium"
            >
              {t('skills.add', 'Tambah')}
            </button>
          </form>

          <div className="border border-slate-200 rounded-md overflow-hidden">
            <div className="bg-slate-50 px-3 py-2 border-b border-slate-200 text-xs font-semibold text-slate-700">
              {t('skills.mySkillsList', 'Daftar Keahlian')} ({skills.length})
            </div>
            {skills.length === 0 ? (
              <div className="p-4 text-center text-xs text-slate-400">
                {t('skills.noSkillsYet', 'Belum ada keahlian yang ditambahkan.')}
              </div>
            ) : (
              <ul className="divide-y divide-slate-100">
                {skills.map((s, idx) => (
                  <li key={idx} className="flex items-center justify-between px-3 py-2 text-xs">
                    <span className="font-medium text-slate-900">{s.skillName}</span>
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-700">
                        {s.proficiencyLevel}
                      </span>
                      <button
                        type="button"
                        onClick={() => handleRemoveSkill(idx)}
                        className="text-rose-500 hover:text-rose-700 hover:bg-rose-50 p-1 rounded"
                      >
                        ✕
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="px-5 py-3 border-t border-slate-200 flex justify-end gap-2 bg-slate-50">
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1.5 border border-slate-300 text-slate-700 hover:bg-white rounded text-xs"
          >
            {t('common.cancel', 'Batal')}
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={updateMutation.isPending}
            className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded text-xs font-medium disabled:opacity-50"
          >
            {updateMutation.isPending ? t('common.saving', 'Menyimpan...') : t('common.save', 'Simpan')}
          </button>
        </div>
      </div>
    </div>
  );
};

