// Enums
export type GroundingStatus = 'GROUNDED' | 'LIMITED_GROUNDING' | 'REFUSED';
export type ResponseMode =
  | 'STANDARD'
  | 'CLARIFICATION_NEEDED'
  | 'SCENARIO_ANALYSIS'
  | 'FINAL_ANALYSIS'
  | 'REFUSED';
export type SourceStatus = 'PENDING' | 'APPROVED' | 'ACTIVE' | 'REJECTED';
export type SourceType = 'URL' | 'PDF' | 'DOCX' | 'STRUCTURED';

// Chat DTOs
export interface ChatAnswerResponse {
  groundingStatus: GroundingStatus;
  threadId: string;
  responseMode: ResponseMode;
  answer: string | null;
  conclusion: string | null;
  disclaimer: string | null;
  uncertaintyNotice: string | null;
  legalBasis: string[];
  penalties: string[];
  requiredDocuments: string[];
  procedureSteps: string[];
  nextSteps: string[];
  pendingFacts: PendingFactResponse[];
  rememberedFacts: RememberedFactResponse[];
  scenarioAnalysis: ScenarioAnalysisResponse | null;
  citations: CitationResponse[];
  sources: SourceReferenceResponse[];
}

export interface ScenarioAnalysisResponse {
  facts: string[];
  rule: string;
  outcome: string;
  actions: string[];
  sources: SourceReferenceResponse[];
}

export interface PendingFactResponse {
  code: string;
  prompt: string;
  reason: string;
}

export interface RememberedFactResponse {
  key: string;
  value: string;
  status: string;
}

export interface CitationResponse {
  inlineLabel: string;
  sourceTitle: string;
  origin: string;
  pageNumber: number | null;
  sectionRef: string | null;
  excerpt: string;
}

export interface SourceReferenceResponse {
  sourceId: string;
  title: string;
  origin: string;
}

export interface ChatThreadSummaryResponse {
  threadId: string;
  createdAt: string;
  updatedAt: string;
  firstMessage: string | null;
}

export type ChatMessageRole = 'USER' | 'ASSISTANT';
export type ChatMessageType = 'QUESTION' | 'ANSWER' | 'CLARIFICATION';

export interface ChatMessageResponse {
  id: string;
  role: ChatMessageRole;
  messageType: ChatMessageType;
  content: string;
  createdAt: string;
}

// Source DTOs
export interface SourceSummaryResponse {
  id: string;
  title: string;
  sourceType: SourceType;
  status: SourceStatus;
  trustedState: string;
  approvalState: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// Ingestion DTOs
export interface IngestionAcceptedResponse {
  sourceId: string;
  jobId: string;
  status: string;
}

// Chunk/Index DTOs
export interface ChunkReadinessResponse {
  approvedChunks: number;
  trustedChunks: number;
  activeChunks: number;
  eligibleChunks: number;
}

export interface IndexSummaryResponse {
  totalChunks: number;
  approvedChunks: number;
  trustedChunks: number;
  activeChunks: number;
  pendingApprovalChunks: number;
  eligibleChunks: number;
}

// Parameter Set DTOs
export interface AiParameterSetResponse {
  id: string;
  name: string;
  active: boolean;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAiParameterSetRequest {
  name: string;
  content: string;
}

export interface UpdateAiParameterSetRequest {
  name: string;
  content: string;
}
