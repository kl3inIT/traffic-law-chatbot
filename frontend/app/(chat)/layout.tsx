'use client';

import { createContext, useContext, useState } from 'react';
import { useAllowedModels } from '@/hooks/use-parameters';
import type { AllowedModel } from '@/types/api';

interface ChatModelContextValue {
  selectedModelId: string | undefined;
  setSelectedModelId: (id: string) => void;
  allowedModels: AllowedModel[];
  isLoadingModels: boolean;
}

const ChatModelContext = createContext<ChatModelContextValue>({
  selectedModelId: undefined,
  setSelectedModelId: () => {},
  allowedModels: [],
  isLoadingModels: false,
});

export function useChatModel() {
  return useContext(ChatModelContext);
}

export default function ChatLayout({ children }: { children: React.ReactNode }) {
  const { data: allowedModels = [], isLoading: isLoadingModels } = useAllowedModels();
  const [selectedModelId, setSelectedModelId] = useState<string | undefined>(undefined);

  return (
    <ChatModelContext.Provider
      value={{ selectedModelId, setSelectedModelId, allowedModels, isLoadingModels }}
    >
      <div className="flex h-screen">{children}</div>
    </ChatModelContext.Provider>
  );
}
