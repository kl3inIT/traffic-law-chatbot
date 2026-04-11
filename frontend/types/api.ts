// Enums
export type GroundingStatus = 'GROUNDED' | 'LIMITED_GROUNDING' | 'REFUSED';
export type ResponseMode =
  | 'STANDARD'
  | 'CLARIFICATION_NEEDED'
  | 'SCENARIO_ANALYSIS'
  | 'FINAL_ANALYSIS'
  | 'REFUSED';
// SourceStatus matches backend SourceStatus enum
export type SourceStatus = 'DRAFT' | 'READY_FOR_REVIEW' | 'ACTIVE' | 'ARCHIVED' | 'DISABLED';
// ApprovalState matches backend ApprovalState enum
export type ApprovalState = 'PENDING' | 'APPROVED' | 'REJECTED';
// TrustedState matches backend TrustedState enum
export type TrustedState = 'UNTRUSTED' | 'TRUSTED' | 'REVOKED';
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
  scenarioFacts: string[];
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
  structuredResponse?: ChatAnswerResponse | null;
}

// Source DTOs
export interface SourceSummaryResponse {
  id: string;
  title: string;
  sourceType: SourceType;
  status: SourceStatus;
  trustedState: TrustedState;
  approvalState: ApprovalState;
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

export type IngestionJobStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'RETRYING';

export type IngestionStep =
  | 'FETCH'
  | 'PARSE'
  | 'NORMALIZE'
  | 'CHUNK'
  | 'EMBED'
  | 'INDEX'
  | 'FINALIZE';

export interface IngestionJobResponse {
  id: string;
  sourceId: string;
  status: IngestionJobStatus;
  stepName: IngestionStep | null;
  queuedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  retryCount: number;
  errorCode: string | null;
  errorMessage: string | null;
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

export interface ChunkSummaryResponse {
  id: string;
  sourceId: string | null;
  sourceVersionId: string | null;
  chunkOrdinal: number;
  pageNumber: number;
  sectionRef: string | null;
  approvalState: string | null;
  trusted: string | null;
  active: string | null;
  contentPreview: string | null;
  embeddingPreview: number[] | null;
  vectorDimension: number;
}

export interface ChunkDetailResponse {
  id: string;
  content: string | null;
  sourceId: string | null;
  sourceVersionId: string | null;
  chunkOrdinal: number;
  pageNumber: number;
  sectionRef: string | null;
  contentHash: string | null;
  processingVersion: string | null;
  approvalState: string | null;
  trusted: string | null;
  active: string | null;
  origin: string | null;
  embedding: number[] | null;
  vectorDimension: number;
}

// Trust Policy DTOs
export type TrustTier = 'PRIMARY' | 'SECONDARY' | 'MANUAL_REVIEW';

export interface TrustPolicyResponse {
  id: string;
  name: string;
  domainPattern: string | null;
  sourceType: string | null;
  trustTier: TrustTier;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTrustPolicyRequest {
  name: string;
  domainPattern?: string;
  sourceType?: string;
  trustTier: TrustTier;
  description?: string;
}

export type UpdateTrustPolicyRequest = CreateTrustPolicyRequest;

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
