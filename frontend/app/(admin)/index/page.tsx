import { IndexCards } from '@/components/admin/index/index-cards';
import { IndexSourcesTable } from '@/components/admin/index/index-sources-table';

export default function IndexPage() {
  return (
    <div className="p-6 space-y-6">
      <IndexCards />
      <IndexSourcesTable />
    </div>
  );
}
