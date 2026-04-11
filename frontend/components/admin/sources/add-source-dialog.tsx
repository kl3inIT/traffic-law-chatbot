'use client';

import { useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Upload, Link } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { uploadSource, submitUrl } from '@/lib/api/ingestion';
import { queryKeys } from '@/lib/query-keys';

type Mode = 'file' | 'url';

export function AddSourceDialog() {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<Mode>('file');
  const [title, setTitle] = useState('');
  const [publisherName, setPublisherName] = useState('');
  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const onSuccess = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.sources });
    handleClose();
  };

  const uploadMutation = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('Vui lòng chọn tệp');
      if (!title.trim()) throw new Error('Vui lòng nhập tiêu đề');
      return uploadSource({
        file,
        title: title.trim(),
        publisherName: publisherName.trim() || undefined,
      });
    },
    onSuccess,
  });

  const urlMutation = useMutation({
    mutationFn: () => {
      if (!url.trim()) throw new Error('Vui lòng nhập URL');
      return submitUrl({
        url: url.trim(),
        title: title.trim() || undefined,
        publisherName: publisherName.trim() || undefined,
      });
    },
    onSuccess,
  });

  const isPending = uploadMutation.isPending || urlMutation.isPending;
  const error = uploadMutation.error || urlMutation.error;

  const handleClose = () => {
    if (isPending) return;
    setOpen(false);
    setTitle('');
    setPublisherName('');
    setUrl('');
    setFile(null);
    uploadMutation.reset();
    urlMutation.reset();
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (mode === 'file') uploadMutation.mutate();
    else urlMutation.mutate();
  };

  return (
    <>
      <Button onClick={() => setOpen(true)} size="sm">
        <Plus className="mr-2 h-4 w-4" />
        Thêm nguồn
      </Button>

      <Dialog
        open={open}
        onOpenChange={(v) => {
          if (!v) handleClose();
        }}
      >
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>Thêm nguồn tài liệu</DialogTitle>
            <DialogDescription>
              Tải lên tệp PDF/Word hoặc nhập URL để đưa vào cơ sở kiến thức.
            </DialogDescription>
          </DialogHeader>

          {/* Mode toggle */}
          <div className="flex gap-2">
            <Button
              type="button"
              variant={mode === 'file' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMode('file')}
            >
              <Upload className="mr-2 h-4 w-4" />
              Tệp
            </Button>
            <Button
              type="button"
              variant={mode === 'url' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMode('url')}
            >
              <Link className="mr-2 h-4 w-4" />
              URL
            </Button>
          </div>

          <form onSubmit={handleSubmit} className="mt-2 space-y-4">
            {mode === 'file' ? (
              <div className="space-y-1">
                <Label htmlFor="file">Tệp (PDF, DOCX)</Label>
                <input
                  id="file"
                  type="file"
                  accept=".pdf,.doc,.docx"
                  ref={fileRef}
                  className="file:bg-primary file:text-primary-foreground hover:file:bg-primary/90 block w-full cursor-pointer text-sm file:mr-4 file:rounded file:border-0 file:px-3 file:py-1 file:text-sm file:font-medium"
                  onChange={(e) => {
                    const f = e.target.files?.[0] ?? null;
                    setFile(f);
                    if (f && !title) setTitle(f.name.replace(/\.[^.]+$/, ''));
                  }}
                />
              </div>
            ) : (
              <div className="space-y-1">
                <Label htmlFor="url">URL trang web</Label>
                <Input
                  id="url"
                  type="url"
                  placeholder="https://..."
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  required
                />
              </div>
            )}

            <div className="space-y-1">
              <Label htmlFor="title">Tiêu đề {mode === 'url' && '(tùy chọn)'}</Label>
              <Input
                id="title"
                placeholder="Nhập tiêu đề nguồn"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required={mode === 'file'}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="publisher">Tên nhà xuất bản (tùy chọn)</Label>
              <Input
                id="publisher"
                placeholder="Ví dụ: Bộ Giao thông Vận tải"
                value={publisherName}
                onChange={(e) => setPublisherName(e.target.value)}
              />
            </div>

            {error && (
              <Alert variant="destructive">
                <AlertDescription>
                  {error instanceof Error ? error.message : 'Đã có lỗi xảy ra. Vui lòng thử lại.'}
                </AlertDescription>
              </Alert>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={handleClose} disabled={isPending}>
                Hủy
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? 'Đang xử lý…' : 'Gửi'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
