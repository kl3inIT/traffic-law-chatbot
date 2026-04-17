import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock next/navigation
vi.mock('next/navigation', () => ({
  usePathname: () => '/',
  useRouter: () => ({ push: vi.fn() }),
  useParams: () => ({}),
}));

// Mock shadcn Sidebar to render children directly (avoids SidebarProvider dependency)
vi.mock('@/components/ui/sidebar', () => ({
  Sidebar: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="sidebar">{children}</div>
  ),
  SidebarContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  SidebarGroup: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  SidebarGroupContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  SidebarGroupLabel: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  SidebarMenu: ({ children }: { children: React.ReactNode }) => <nav>{children}</nav>,
  SidebarMenuButton: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  SidebarMenuItem: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  SidebarHeader: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  SidebarSeparator: () => <hr />,
}));

import { AppSidebar, adminNavItems } from '@/components/layout/app-sidebar';

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('AppSidebar', () => {
  it('renders the sidebar with correct number of admin nav items', () => {
    renderWithProviders(<AppSidebar />);
    expect(adminNavItems).toHaveLength(7);
  });

  it('renders all admin nav item labels', () => {
    renderWithProviders(<AppSidebar />);
    expect(screen.getByText('Quản lý nguồn')).toBeInTheDocument();
    expect(screen.getByText('Chỉ mục kiến thức')).toBeInTheDocument();
    expect(screen.getByText('Bộ tham số AI')).toBeInTheDocument();
    expect(screen.getByText('Chính sách tin cậy')).toBeInTheDocument();
    expect(screen.getByText('Lịch sử hội thoại')).toBeInTheDocument();
    expect(screen.getByText('Kiểm tra chất lượng')).toBeInTheDocument();
    expect(screen.getByText('Lịch sử chạy')).toBeInTheDocument();
  });

  it('renders the chat section label', () => {
    renderWithProviders(<AppSidebar />);
    expect(screen.getByText('Trò chuyện')).toBeInTheDocument();
  });
});
