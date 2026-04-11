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
  Sidebar: ({ children }: { children: React.ReactNode }) => <div data-testid="sidebar">{children}</div>,
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
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  );
}

describe('AppSidebar', () => {
  it('renders the sidebar with correct number of admin nav items', () => {
    renderWithProviders(<AppSidebar />);
    // 3 admin nav items exported
    expect(adminNavItems).toHaveLength(3);
  });

  it('renders all admin nav item labels', () => {
    renderWithProviders(<AppSidebar />);
    expect(screen.getByText('Quan ly nguon')).toBeInTheDocument();
    expect(screen.getByText('Chi muc kien thuc')).toBeInTheDocument();
    expect(screen.getByText('Bo tham so AI')).toBeInTheDocument();
  });

  it('renders the chat section label', () => {
    renderWithProviders(<AppSidebar />);
    expect(screen.getByText('Tro chuyen')).toBeInTheDocument();
  });
});
