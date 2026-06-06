package com.richie.component.statemachine.config;

import com.richie.component.statemachine.model.State;
import com.richie.component.statemachine.model.StateMachineModel;
import com.richie.component.statemachine.model.Transition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 状态机配置加载器
 * <p>
 * 负责从文件系统或 classpath 加载状态机配置文件（YAML/JSON），
 * 并将配置定义转换为状态机模型对象。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateMachineConfigLoader {

    /**
     * Spring 资源加载器，用于加载配置文件
     */
    private final ResourceLoader resourceLoader;

    /**
     * YAML 对象映射器，用于解析 YAML 格式的配置文件
     */
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * JSON 对象映射器，用于解析 JSON 格式的配置文件
     */
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * 从 YAML 文件加载状态机配置
     * <p>
     * 支持 Ant 风格的路径模式（如 classpath:statemachine\/**\/*.yml），
     * 会扫描所有匹配的 YAML 文件并解析为状态机定义。
     *
     *
     * @param configPath 配置文件路径（支持 Ant 风格模式，如 classpath:statemachine\/**\/*.yml）
     * @return 状态机定义列表，如果路径为空或未找到文件则返回空列表
     */
    public List<StateMachineDefinition> loadFromYaml(String configPath) {
        List<StateMachineDefinition> definitions = new ArrayList<>();

        if (configPath == null || configPath.isBlank()) {
            log.warn("配置文件路径为空，跳过 YAML 加载");
            return definitions;
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            Resource[] resources = resolver.getResources(configPath);

            if (resources.length == 0) {
                log.debug("未找到匹配的 YAML 配置文件: {}", configPath);
                return definitions;
            }

            for (Resource resource : resources) {
                if (resource == null || !resource.exists()) {
                    continue;
                }

                try {
                    StateMachineDefinition definition = yamlMapper.readValue(resource.getInputStream(), StateMachineDefinition.class);
                    if (definition != null && definition.getName() != null) {
                        definitions.add(definition);
                        log.info("加载状态机配置: {} from {}", definition.getName(), resource.getFilename());
                    } else {
                        log.warn("配置文件内容无效（缺少名称）: {}", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.error("加载配置文件失败: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.error("扫描配置文件失败: {}", configPath, e);
        }

        return definitions;
    }

    /**
     * 从 JSON 文件加载状态机配置
     * <p>
     * 支持 Ant 风格的路径模式（如 classpath:statemachine\/**\/*.json），
     * 会扫描所有匹配的 JSON 文件并解析为状态机定义。
     *
     *
     * @param configPath 配置文件路径（支持 Ant 风格模式，如 classpath:statemachine\/**\/*.json）
     * @return 状态机定义列表，如果路径为空或未找到文件则返回空列表
     */
    public List<StateMachineDefinition> loadFromJson(String configPath) {
        List<StateMachineDefinition> definitions = new ArrayList<>();

        if (configPath == null || configPath.isBlank()) {
            log.warn("配置文件路径为空，跳过 JSON 加载");
            return definitions;
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            Resource[] resources = resolver.getResources(configPath);

            if (resources.length == 0) {
                log.debug("未找到匹配的 JSON 配置文件: {}", configPath);
                return definitions;
            }

            for (Resource resource : resources) {
                if (resource == null || !resource.exists()) {
                    continue;
                }

                try {
                    StateMachineDefinition definition = jsonMapper.readValue(resource.getInputStream(), StateMachineDefinition.class);
                    if (definition != null && definition.getName() != null) {
                        definitions.add(definition);
                        log.info("加载状态机配置: {} from {}", definition.getName(), resource.getFilename());
                    } else {
                        log.warn("配置文件内容无效（缺少名称）: {}", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.error("加载配置文件失败: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.error("扫描配置文件失败: {}", configPath, e);
        }

        return definitions;
    }

    /**
     * 将配置定义转换为状态机模型对象
     * <p>
     * 将从配置文件加载的状态机定义转换为运行时使用的状态机模型。
     * 包括状态转换、类型校验、初始状态设置等。
     *
     *
     * @param definition 状态机定义对象
     * @return 状态机模型对象
     * @throws IllegalArgumentException 如果定义为 null、名称为空、状态定义无效或转换定义无效
     */
    public StateMachineModel convertToStateMachine(StateMachineDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("状态机定义不能为 null");
        }
        if (definition.getName() == null || definition.getName().isBlank()) {
            throw new IllegalArgumentException("状态机名称不能为空");
        }

        StateMachineModel stateMachine = new StateMachineModel(definition.getName(), definition.getDescription());

        // 设置初始状态
        if (definition.getStates() != null && !definition.getStates().isEmpty()) {
            for (StateMachineDefinition.StateDefinition stateDef : definition.getStates()) {
                if (stateDef == null || stateDef.getName() == null || stateDef.getName().isBlank()) {
                    log.warn("跳过无效的状态定义: {}", stateDef);
                    continue;
                }

                try {
                    State state = new State(stateDef.getName(), stateDef.getDescription());
                    // 容错处理：如果类型无效，默认使用 NORMAL
                    try {
                        state.setType(State.StateType.valueOf(stateDef.getType() != null ? stateDef.getType() : "NORMAL"));
                    } catch (IllegalArgumentException e) {
                        log.warn("状态类型无效: {}，使用默认类型 NORMAL", stateDef.getType());
                        state.setType(State.StateType.NORMAL);
                    }
                    stateMachine.addState(state);

                    // 如果是初始状态，设置为初始状态
                    if ("INITIAL".equalsIgnoreCase(stateDef.getType())) {
                        stateMachine.setInitialState(stateDef.getName());
                    }
                } catch (Exception e) {
                    log.error("处理状态定义失败: {}", stateDef.getName(), e);
                    throw new IllegalArgumentException("处理状态定义失败: " + stateDef.getName(), e);
                }
            }
        } else {
            log.warn("状态机 {} 没有定义任何状态", definition.getName());
        }

        // 添加转换
        if (definition.getTransitions() != null && !definition.getTransitions().isEmpty()) {
            for (StateMachineDefinition.TransitionDefinition transitionDef : definition.getTransitions()) {
                if (transitionDef == null) {
                    log.warn("跳过 null 转换定义");
                    continue;
                }

                try {
                    // 验证必要字段
                    if (transitionDef.getFromState() == null && transitionDef.getToState() == null) {
                        log.warn("跳过无效的转换定义（缺少 fromState 或 toState）: {}", transitionDef.getName());
                        continue;
                    }

                    Transition transition = new Transition(
                        transitionDef.getName() != null ? transitionDef.getName() : "",
                        transitionDef.getFromState(),
                        transitionDef.getToState(),
                        transitionDef.getEvent()
                    );
                    transition.setDescription(transitionDef.getDescription());
                    transition.setCondition(transitionDef.getCondition());
                    transition.setAction(transitionDef.getAction());
                    transition.setPriority(transitionDef.getPriority());

                    // 支持 extensions 和 attributes 两种写法（兼容性）
                    Map<String, Object> attributes = transitionDef.getAttributes();
                    if (attributes == null && definition.getExtensions() != null) {
                        // 如果 transition 没有 attributes，尝试从全局 extensions 获取
                        attributes = definition.getExtensions();
                    }
                    transition.setAttributes(attributes);

                    stateMachine.addTransition(transition);
                } catch (Exception e) {
                    log.error("处理转换定义失败: {}", transitionDef.getName(), e);
                    throw new IllegalArgumentException("处理转换定义失败: " + transitionDef.getName(), e);
                }
            }
        } else {
            log.warn("状态机 {} 没有定义任何转换", definition.getName());
        }

        // 验证初始状态是否已设置
        if (stateMachine.getInitialState() == null) {
            log.warn("状态机 {} 未设置初始状态，将使用第一个状态作为初始状态", definition.getName());
            if (!stateMachine.getStates().isEmpty()) {
                stateMachine.setInitialState(stateMachine.getStates().getFirst().getName());
            }
        }

        return stateMachine;
    }
}
