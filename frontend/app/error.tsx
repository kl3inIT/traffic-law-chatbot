'use client';

import { useEffect } from 'react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { AlertTriangle } from 'lucide-react';

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('[GlobalError]', error);
  }, [error]);

  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <div className="w-full max-w-md space-y-4">
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>Đã xảy ra lỗi</AlertTitle>
          <AlertDescription>
            {error.message || 'Đã có lỗi không mong muốn. Vui lòng thử lại.'}
          </AlertDescription>
        </Alert>
        <Button variant="outline" onClick={reset}>
          Thử lại
        </Button>
      </div>
    </div>
  );
}
