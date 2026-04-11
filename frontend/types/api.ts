// Enums
export type GroundingStatus = 'GROUNDED' | 'LIMITED_GROUNDING' | 'REFUSED';
export type ResponseMode = 'STANDARD' | 'CLARIFICATION_NEEDED' | 'SCENARIO_ANALYSIS' | 'FINAL_ANALYSIS' | 'REFUSED';
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
  factKey: string;
  question: string;
  explanation: string;
}

export interface RememberedFactResponse {
  factKey: string;
  factValue: string;
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
  page: number;
  size: number;
}

// Chunk/Index DTOs
export interface ChunkReadinessResponse {
  approvedCount: number;
  trustedCount: number;
  activeCount: number;
  eligibleCount: number;
}

export interface IndexSummaryResponse {
  totalChunks: number;
  activeChunks: number;
  inactiveChunks: number;
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
