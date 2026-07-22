package com.hr.resumematch.service;

import com.hr.resumematch.dto.ApiDtos.CandidateProfile;
import com.hr.resumematch.dto.ApiDtos.RiskFlag;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RiskDetector {

    public List<RiskFlag> detect(CandidateProfile profile, String text) {
        List<RiskFlag> risks = new ArrayList<>();
        if (profile.getYearsOfExperience() != null && profile.getYearsOfExperience() < 2) {
            RiskFlag r = new RiskFlag();
            r.setType("经验偏少");
            r.setSeverity("medium");
            r.setDetail("工作年限较短，需核验独立交付能力");
            risks.add(r);
        }
        if (profile.getSkills() != null && profile.getSkills().size() > 12) {
            RiskFlag r = new RiskFlag();
            r.setType("技能夸大");
            r.setSeverity("medium");
            r.setDetail("技能列表过长，建议核验主力技术栈");
            risks.add(r);
        }
        if (text != null && (text.contains("空窗") || text.contains("待业"))) {
            RiskFlag r = new RiskFlag();
            r.setType("履历风险");
            r.setSeverity("high");
            r.setDetail("简历提及空窗/待业，建议追问");
            risks.add(r);
        }
        if (risks.isEmpty()) {
            RiskFlag r = new RiskFlag();
            r.setType("常规核验");
            r.setSeverity("low");
            r.setDetail("无明显高风险标记，仍建议核验项目贡献度");
            risks.add(r);
        }
        return risks;
    }
}
