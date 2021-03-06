package de.jgh.spring.jobdb.backend.jobdbbackend.aspect;

import de.jgh.spring.jobdb.backend.jobdbbackend.annotation.NoJobLog;
import de.jgh.spring.jobdb.backend.jobdbbackend.dto.ScheduledJobResult;
import de.jgh.spring.jobdb.backend.jobdbbackend.model.Job;
import de.jgh.spring.jobdb.backend.jobdbbackend.model.JobDefinition;
import de.jgh.spring.jobdb.backend.jobdbbackend.repository.JobDefinitionRepository;
import de.jgh.spring.jobdb.backend.jobdbbackend.repository.JobRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

import static de.jgh.spring.jobdb.backend.jobdbbackend.model.JobStatus.ERROR;
import static de.jgh.spring.jobdb.backend.jobdbbackend.model.JobStatus.FINISHED;

@Aspect
@Configuration
@Slf4j
public class ScheduledAspect {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobDefinitionRepository jobDefinitionRepository;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object logJobexecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        NoJobLog noJobLogAnnotation = method.getAnnotation(NoJobLog.class);

        Scheduled scheduledAnnotation = method.getAnnotation(Scheduled.class);
        String cronExpression = scheduledAnnotation.cron();
        String jobType = StringUtils.capitalize(method.getName());

        JobDefinition jobDefinition = jobDefinitionRepository.findByJobType(jobType).orElseGet(() -> new JobDefinition(cronExpression));
        jobDefinition.setJobType(jobType);
        jobDefinition.setCronExpression(cronExpression);
        jobDefinition.setClassName(method.getDeclaringClass().getName());
        jobDefinition.setMethodName(method.getName());
        jobDefinitionRepository.save(jobDefinition);
        Job job = null;
        if (noJobLogAnnotation == null) {
            job = new Job();
            job.setJobDefinition(jobDefinition);
            job = jobRepository.save(job);
        }

        ScheduledJobResult scheduledJobResult = null;
        try {
            scheduledJobResult = (ScheduledJobResult) joinPoint.proceed();
            if (scheduledJobResult != null && noJobLogAnnotation == null) {
                job.setCntImported(scheduledJobResult.getImportCnt());
                job.setIdentifiers(scheduledJobResult.getIdentifiers());
                job.setJobStatus(FINISHED);
            }
        } catch (Exception e) {
            if (noJobLogAnnotation == null) {
                job.setJobStatus(ERROR);
                job.setMeta(ExceptionUtils.getStackTrace(e));
            }
            log.error("error occured in job execution: " + jobType, e);
        } finally {
            if (noJobLogAnnotation == null) {
                jobRepository.save(job);
            }
        }

        return scheduledJobResult;

    }

}
