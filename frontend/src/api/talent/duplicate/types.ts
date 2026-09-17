/**
 * 重复人才预警类型定义（对齐 TlTalentDuplicateVo / TlDuplicateConfirmBo）
 */
export interface TalentDuplicateVO extends BaseEntity {
  duplicateId?: string | number;
  sourceTalentId?: string | number;
  sourceName?: string;
  sourceTalentNo?: string;
  matchedTalentId?: string | number;
  matchedName?: string;
  matchedTalentNo?: string;
  matchedRegionCode?: string;
  matchRule?: string;
  matchScore?: number;
  conclusion?: string;
  conclusionLabel?: string;
  confirmBy?: string | number;
  confirmByName?: string;
  confirmTime?: string;
  confirmRemark?: string;
}

export interface TalentDuplicateQuery extends PageQuery {
  sourceTalentId?: string | number;
  matchedTalentId?: string | number;
  sourceName?: string;
  matchedName?: string;
  conclusion?: string;
  matchRule?: string;
  params?: Record<string, any>;
}

export interface TalentDuplicateConfirmForm {
  duplicateId: string | number;
  conclusion: string;
  confirmRemark?: string;
}
