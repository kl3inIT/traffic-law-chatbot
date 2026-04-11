'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { useCreateParameterSet, useUpdateParameterSet } from '@/hooks/use-parameters';
import type { AiParameterSetResponse } from '@/types/api';

const parameterSetSchema = z.object({
  name: z.string().min(1, 'Tên bộ tham số là bắt buộc'),
  content: z.string().min(1, 'Nội dung YAML là bắt buộc'),
});

type FormValues = z.infer<typeof parameterSetSchema>;

interface ParameterDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editTarget?: AiParameterSetResponse | null;
}

export function ParameterDialog({ open, onOpenChange, editTarget }: ParameterDialogProps) {
  const createMutation = useCreateParameterSet();
  const updateMutation = useUpdateParameterSet();
  const isEdit = !!editTarget;

  const form = useForm<FormValues>({
    resolver: zodResolver(parameterSetSchema),
    defaultValues: { name: '', content: '' },
  });

  useEffect(() => {
    if (editTarget) {
      form.reset({ name: editTarget.name, content: editTarget.content });
    } else {
      form.reset({ name: '', content: '' });
    }
  }, [editTarget, form]);

  const onSubmit = async (values: FormValues) => {
    try {
      if (isEdit && editTarget) {
        await updateMutation.mutateAsync({ id: editTarget.id, data: values });
      } else {
        await createMutation.mutateAsync(values);
      }
      onOpenChange(false);
      form.reset();
    } catch {
      // Lỗi hiển thị inline
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isError = createMutation.isError || updateMutation.isError;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? 'Chỉnh sửa bộ tham số' : 'Tạo bộ tham số'}
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name">Tên bộ tham số</Label>
            <Input
              id="name"
              {...form.register('name')}
              placeholder="Nhập tên bộ tham số"
            />
            {form.formState.errors.name && (
              <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="content">Nội dung YAML</Label>
            <Textarea
              id="content"
              {...form.register('content')}
              className="font-mono text-sm min-h-[320px]"
              placeholder="model:\n  name: openai\n  temperature: 0.3"
            />
            {form.formState.errors.content && (
              <p className="text-xs text-destructive">{form.formState.errors.content.message}</p>
            )}
          </div>

          {isError && (
            <Alert variant="destructive">
              <AlertDescription>
                Thao tác không thành công. Vui lòng thử lại hoặc liên hệ quản trị viên.
              </AlertDescription>
            </Alert>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? 'Đang xử lý...' : isEdit ? 'Lưu' : 'Tạo mới'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
