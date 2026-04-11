export default async function ThreadPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params; // Next.js 16 async params (Pitfall 2)
  return (
    <div className="flex flex-1 flex-col">
      <div className="p-4">
        <p className="text-sm text-muted-foreground">Thread: {id}</p>
      </div>
    </div>
  );
}
