'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useChatLogs } from '@/hooks/use-chat-logs';
import type { GroundingStatus, ChatLogListItem } from '@/types/api';

const PAGE_SIZE = 20;

function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

const groundingStatusLabel: Record<GroundingStatus, string> = {
  GROUNDED: 'Đã dẫn nguồn',
  LIMITED_GROUNDING: 'Dẫn nguồn hạn chế',
  REFUSED: 'Từ chối trả lời',
};

const groundingStatusClass: Record<GroundingStatus, string> = {
  GROUNDED: 'bg-green-100 text-green-800 border-green-200',
  LIMITED_GROUNDING: 'bg-yellow-100 text-yellow-800 border-yellow-200',
  REFUSED: 'bg-red-100 text-red-800 border-red-200',
};

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

export default function ChatLogsPage() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [groundingStatusFilter, setGroundingStatusFilter] = useState<GroundingStatus | ''>('');
  const [searchText, setSearchText] = useState('');
  const debouncedSearch = useDebounced(searchText, 300);

  const filters = {
    page,
    size: PAGE_SIZE,
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
    ...(groundingStatusFilter ? { groundingStatus: groundingStatusFilter } : {}),
    ...(debouncedSearch ? { q: debouncedSearch } : {}),
  };

  const { data, isLoading, isError, refetch } = useChatLogs(filters);

  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="space-y-4 p-6">
      <h1 className="text-xl font-semibold">Lịch sử hội thoại</h1>

      <div className="bg-muted flex flex-wrap items-center gap-3 rounded-md border px-4 py-2">
        <div className="flex items-center gap-2">
          <Label className="text-sm whitespace-nowrap">Từ ngày</Label>
          <Input
            type="date"
            className="h-8 w-36 text-sm"
            value={from}
            onChange={(e) => { setFrom(e.target.value); setPage(0); }}
          />
        </div>
        <div className="flex items-center gap-2">
          <Label className="text-sm whitespace-nowrap">Đến ngày</Label>
          <Input
            type="date"
            className="h-8 w-36 text-sm"
            value={to}
            onChange={(e) => { setTo(e.target.value); setPage(0); }}
          />
        </div>
        <div className="flex items-center gap-2">
          <Label className="text-sm whitespace-nowrap">Trạng thái</Label>
          <Select
            value={groundingStatusFilter}
            onValueChange={(v) => {
              setGroundingStatusFilter(v as GroundingStatus | '');
              setPage(0);
            }}
          >
            <SelectTrigger className="h-8 w-44 text-sm">
              <SelectValue placeholder="Tất cả" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="">Tất cả</SelectItem>
              <SelectItem value="GROUNDED">Đã dẫn nguồn</SelectItem>
              <SelectItem value="LIMITED_GROUNDING">Dẫn nguồn hạn chế</SelectItem>
              <SelectItem value="REFUSED">Từ chối trả lời</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <Input
          type="text"
          placeholder="Tìm kiếm theo câu hỏi..."
          className="h-8 w-56 text-sm"
          value={searchText}
          onChange={(e) => { setSearchText(e.target.value); setPage(0); }}
        />
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : isError ? (
        <div className="space-y-3">
          <p className="text-sm text-destructive">Không thể tải dữ liệu. Vui lòng thử lại.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Thử lại
          </Button>
        </div>
      ) : data && data.content.length === 0 ? (
        <div className="py-8 text-center">
          <h2 className="text-xl font-semibold">Chưa có lịch sử hội thoại</h2>
          <p className="text-muted-foreground mt-2 text-sm">
            Các cuộc hội thoại sẽ xuất hiện ở đây khi người dùng bắt đầu trò chuyện.
          </p>
        </div>
      ) : (
        <>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-40">Ngày tạo</TableHead>
                  <TableHead>Câu hỏi</TableHead>
                  <TableHead className="w-36">Trạng thái</TableHead>
                  <TableHead className="w-32">Token (P+C)</TableHead>
                  <TableHead className="w-32">Thời gian (ms)</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(data?.content ?? []).map((log: ChatLogListItem) => (
                  <TableRow
                    key={log.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => router.push(`/chat-logs/${log.id}`)}
                  >
                    <TableCell className="text-sm">{formatDate(log.createdDate)}</TableCell>
                    <TableCell>
                      <span className="truncate max-w-xs block text-sm">{log.question}</span>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className={groundingStatusClass[log.groundingStatus]}>
                        {groundingStatusLabel[log.groundingStatus]}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm">
                      P: {log.promptTokens} / C: {log.completionTokens}
                    </TableCell>
                    <TableCell className="text-sm">{log.responseTime}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {totalPages > 1 && (
            <div className="text-muted-foreground flex items-center justify-between text-xs">
              <span>
                Trang {page + 1} / {totalPages} ({totalElements} bản ghi)
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 px-3 text-xs"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  Trước
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 px-3 text-xs"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Sau
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
