'use client';

import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useSources } from '@/hooks/use-sources';

export function IndexSourcesTable() {
  const { data, isLoading } = useSources();
  const activeSources = data?.content.filter((s) => s.status === 'ACTIVE') ?? [];

  return (
    <div className="space-y-2">
      <h2 className="text-base font-semibold">Nguồn đang được index ({activeSources.length})</h2>
      <div className="border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-muted text-muted-foreground">
            <tr>
              <th className="text-left px-4 py-2 font-medium">Tiêu đề</th>
              <th className="text-left px-4 py-2 font-medium">Loại</th>
              <th className="text-left px-4 py-2 font-medium">Tin cậy</th>
              <th className="text-left px-4 py-2 font-medium">Ngày tạo</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              Array.from({ length: 3 }).map((_, i) => (
                <tr key={i} className="border-t">
                  <td className="px-4 py-2" colSpan={4}><Skeleton className="h-4 w-full" /></td>
                </tr>
              ))
            )}
            {!isLoading && activeSources.length === 0 && (
              <tr className="border-t">
                <td className="px-4 py-3 text-muted-foreground text-center" colSpan={4}>
                  Chưa có nguồn nào được kích hoạt vào index
                </td>
              </tr>
            )}
            {activeSources.map((src) => (
              <tr key={src.id} className="border-t hover:bg-muted/40">
                <td className="px-4 py-2 max-w-[280px] truncate">{src.title}</td>
                <td className="px-4 py-2 text-muted-foreground">{src.sourceType}</td>
                <td className="px-4 py-2">
                  <Badge variant={src.trustedState === 'TRUSTED' ? 'default' : 'secondary'} className="text-xs">
                    {src.trustedState === 'TRUSTED' ? 'Tin cậy' : 'Chưa tin cậy'}
                  </Badge>
                </td>
                <td className="px-4 py-2 text-muted-foreground">
                  {new Date(src.createdAt).toLocaleDateString('vi-VN')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
