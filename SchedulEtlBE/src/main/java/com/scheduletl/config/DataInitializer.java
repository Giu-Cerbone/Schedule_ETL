package com.scheduletl.config;

import com.scheduletl.domain.*;
import com.scheduletl.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    private final UserProfileRepository userProfileRepository;
    private final UserGroupRepository userGroupRepository;
    private final SchedulerRepository schedulerRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessTypeRepository processTypeRepository;

    public DataInitializer(UserProfileRepository userProfileRepository,
                          UserGroupRepository userGroupRepository,
                          SchedulerRepository schedulerRepository,
                          ProcessDefinitionRepository processDefinitionRepository,
                          ProcessTypeRepository processTypeRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userGroupRepository = userGroupRepository;
        this.schedulerRepository = schedulerRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.processTypeRepository = processTypeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureProcessTypes();
        migrateLegacyShellCommandType();

        if (userProfileRepository.count() > 0) {
            return;
        }

        UserProfile admin = new UserProfile();
        admin.setUsername("admin");
        admin.setFullName("System Administrator");
        admin.setEmail("admin@scheduletl.local");
        admin = userProfileRepository.save(admin);

        UserProfile analyst = new UserProfile();
        analyst.setUsername("analyst");
        analyst.setFullName("Data Analyst");
        analyst.setEmail("analyst@scheduletl.local");
        analyst = userProfileRepository.save(analyst);

        UserGroup opsGroup = new UserGroup();
        opsGroup.setName("OPERATIONS");
        opsGroup.setDescription("Operational processes and automation");
        opsGroup = userGroupRepository.save(opsGroup);

        admin.getGroups().add(opsGroup);
        opsGroup.getMembers().add(admin);
        userProfileRepository.save(admin);
        userGroupRepository.save(opsGroup);

        Scheduler scheduler = new Scheduler();
        scheduler.setName("Night ETL Batch");
        scheduler.setDescription("Main scheduler for night ETL and reporting tasks");
        scheduler.setCronExpression("0 0 2 * * ?");
        scheduler.setStatus(SchedulerStatus.ACTIVE);
        scheduler.setOwnerProfile(admin);
        scheduler.setGroup(opsGroup);
        scheduler = schedulerRepository.save(scheduler);

        ProcessDefinition extraction = new ProcessDefinition();
        extraction.setName("Extract Sales Data");
        extraction.setType(processTypeRepository.getReferenceById("SQLDATABASE"));
        extraction.setDescription("Extract sales records from source system");
        extraction.setConfiguration("{\"source\":\"crm\", \"mode\":\"full\"}");
        extraction.setEnabled(true);
        extraction.setScheduler(scheduler);
        processDefinitionRepository.save(extraction);

        ProcessDefinition transformation = new ProcessDefinition();
        transformation.setName("Normalize Data");
        transformation.setType(processTypeRepository.getReferenceById("SCRIPT"));
        transformation.setDescription("Normalize and aggregate extracted data");
        transformation.setConfiguration("{\"ruleSet\":\"sales-v1\"}");
        transformation.setEnabled(true);
        transformation.setScheduler(scheduler);
        processDefinitionRepository.save(transformation);
    }

    private void ensureProcessTypes() {
        saveProcessTypeIfMissing("SQLDATABASE", "Esecuzione di operazioni su database SQL");
        saveProcessTypeIfMissing("SHELL_CMD", "Esecuzione di comandi shell");
        saveProcessTypeIfMissing("SCRIPT", "Esecuzione di script");
    }

    private void migrateLegacyShellCommandType() {
        if (!processTypeRepository.existsById("SHECMD")) {
            return;
        }

        ProcessType shellCommandType = processTypeRepository.getReferenceById("SHELL_CMD");
        List<ProcessDefinition> legacyProcesses = processDefinitionRepository.findByTypeCode("SHECMD");
        legacyProcesses.forEach(process -> process.setType(shellCommandType));
        processDefinitionRepository.saveAll(legacyProcesses);
        processTypeRepository.deleteById("SHECMD");
    }

    private void saveProcessTypeIfMissing(String code, String description) {
        if (!processTypeRepository.existsById(code)) {
            ProcessType processType = new ProcessType();
            processType.setCode(code);
            processType.setDescription(description);
            processTypeRepository.save(processType);
        }
    }

}
