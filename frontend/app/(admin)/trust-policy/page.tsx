'use client';

import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';
import {
  useTrustPolicies,
  useCreateTrustPolicy,
  useUpdateTrustPolicy,
  useDeleteTrustPolicy,
} from '@/hooks/use-trust-policy';
import type { TrustPolicyResponse, TrustTier } from '@/types/api';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

const schema = z.object({
  name: z.string().min(1, 'Tên chính sách là bắt buộc'),
  domainPattern: z.string().optional(),
  sourceType: z.string().optional(),
  trustTier: z.enum(['PRIMARY', 'SECONDARY', 'MANUAL_REVIEW']),
  description: z.string().optional(),
});
type FormValues = z.infer<typeof schema>;

const tierBadgeClass: Record<TrustTier, string> = {
  PRIMARY: 'bg-green-100 text-green-800 border-green-200',
  SECONDARY: 'bg-yellow-100 text-yellow-800 border-yellow-200',
  MANUAL_REVIEW: 'bg-gray-100 text-gray-800 border-gray-200',
};

const tierLabel: Record<TrustTier, string> = {
  PRIMARY: 'Chính',
  SECONDARY: 'Phụ',
  MANUAL_REVIEW: 'Xem xét',
};

export default function TrustPolicyPage() {
  const { data: policies, isLoading } = useTrustPolicies();
  const [selected, setSelected] = useState<TrustPolicyResponse | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<TrustPolicyResponse | null>(null);

  const createMutation = useCreateTrustPolicy();
  const updateMutation = useUpdateTrustPolicy();
  const deleteMutation = useDeleteTrustPolicy();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', domainPattern: '', sourceType: '', trustTier: 'PRIMARY', description: '' },
  });

  const openNew = () => {
    setSelected(null);
    setIsNew(true);
    form.reset({ name: '', domainPattern: '', sourceType: '', trustTier: 'PRIMARY', description: '' });
  };

  const openEdit = (policy: TrustPolicyResponse) => {
    setSelected(policy);
    setIsNew(false);
    form.reset({
      name: policy.name,
      domainPattern: policy.domainPattern ?? '',
      sourceType: policy.sourceType ?? '',
      trustTier: policy.trustTier,
      description: policy.description ?? '',
    });
  };

  const onSubmit = async (values: FormValues) => {
    if (isNew) {
      await createMutation.mutateAsync(values);
      setIsNew(false);
      form.reset();
    } else if (selected) {
      await updateMutation.mutateAsync({ id: selected.id, data: values });
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isError = createMutation.isError || updateMutation.isError;
  const showEditor = isNew || selected !== null;

  return (
    <div className="p-6 flex flex-col gap-4 h-screen">
      <div className="flex items-center justify-between flex-shrink-0">
        <h1 className="text-xl font-semibold">Chính sách tin cậy</h1>
        <Button onClick={openNew}>+ Tạo mới</Button>
      </div>

      <div className="flex gap-4 flex-1 min-h-0">
        {/* Danh sách bên trái */}
        <div className="w-64 flex-shrink-0 border rounded-lg overflow-y-auto">
          {isLoading && (
            <div className="p-3 space-y-2">
              {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
            </div>
          )}
          {policies?.map((policy) => (
            <div
              key={policy.id}
              onClick={() => openEdit(policy)}
              className={cn(
                'p-3 border-b cursor-pointer hover:bg-muted transition-colors',
                selected?.id === policy.id && !isNew && 'bg-primary/10 border-l-2 border-l-primary'
              )}
            >
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-sm font-medium truncate">{policy.name}</span>
                <Badge
                  variant="outline"
                  className={cn('text-xs', tierBadgeClass[policy.trustTier])}
                >
                  {tierLabel[policy.trustTier]}
                </Badge>
              </div>
              {policy.domainPattern && (
                <p className="text-xs text-muted-foreground mt-1 truncate">{policy.domainPattern}</p>
              )}
            </div>
          ))}
          {!isLoading && policies?.length === 0 && (
            <p className="p-4 text-sm text-muted-foreground text-center">Chưa có chính sách</p>
          )}
        </div>

        {/* Editor bên phải */}
        <div className="flex-1 border rounded-lg overflow-hidden flex flex-col">
          {!showEditor ? (
            <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
              Chọn chính sách để chỉnh sửa hoặc nhấn &quot;+ Tạo mới&quot;
            </div>
          ) : (
            <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col h-full p-4 gap-4 overflow-y-auto">
              <div className="space-y-1">
                <Label htmlFor="name">Tên chính sách</Label>
                <Input id="name" {...form.register('name')} placeholder="Nhập tên chính sách" />
                {form.formState.errors.name && (
                  <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
                )}
              </div>

              <div className="space-y-1">
                <Label htmlFor="trustTier">Cấp độ tin cậy</Label>
                <select
                  id="trustTier"
                  {...form.register('trustTier')}
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  <option value="PRIMARY">PRIMARY — Chính thức</option>
                  <option value="SECONDARY">SECONDARY — Phụ trợ</option>
                  <option value="MANUAL_REVIEW">MANUAL_REVIEW — Xem xét thủ công</option>
                </select>
                {form.formState.errors.trustTier && (
                  <p className="text-xs text-destructive">{form.formState.errors.trustTier.message}</p>
                )}
              </div>

              <div className="space-y-1">
                <Label htmlFor="domainPattern">Mẫu tên miền (tuỳ chọn)</Label>
                <Input
                  id="domainPattern"
                  {...form.register('domainPattern')}
                  placeholder="vd: *.gov.vn"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="sourceType">Loại nguồn (tuỳ chọn)</Label>
                <Input
                  id="sourceType"
                  {...form.register('sourceType')}
                  placeholder="vd: PDF, URL, DOCX"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="description">Mô tả (tuỳ chọn)</Label>
                <Textarea
                  id="description"
                  {...form.register('description')}
                  className="resize-none"
                  rows={3}
                  placeholder="Mô tả ngắn về chính sách này"
                />
              </div>

              {isError && (
                <Alert variant="destructive">
                  <AlertDescription>Thao tác không thành công. Vui lòng thử lại.</AlertDescription>
                </Alert>
              )}

              <div className="flex gap-2 justify-between mt-auto">
                {selected && !isNew && (
                  <Button
                    type="button"
                    variant="outline"
                    className="text-destructive border-destructive hover:bg-destructive hover:text-destructive-foreground"
                    onClick={() => setDeleteTarget(selected)}
                  >
                    Xóa
                  </Button>
                )}
                <div className="flex gap-2 ml-auto">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => { setSelected(null); setIsNew(false); form.reset(); }}
                  >
                    Hủy
                  </Button>
                  <Button type="submit" disabled={isPending}>
                    {isPending ? 'Đang xử lý...' : isNew ? 'Tạo mới' : 'Lưu thay đổi'}
                  </Button>
                </div>
              </div>
            </form>
          )}
        </div>
      </div>

      {/* Xác nhận xóa */}
      <AlertDialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Xóa chính sách &quot;{deleteTarget?.name}&quot;? Dữ liệu sẽ bị xóa vĩnh viễn.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={() => {
                if (deleteTarget) {
                  deleteMutation.mutate(deleteTarget.id);
                  if (selected?.id === deleteTarget.id) { setSelected(null); }
                  setDeleteTarget(null);
                }
              }}
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
