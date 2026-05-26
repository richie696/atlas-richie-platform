/**
 * 抽象枚举基类
 * 提供类似Java枚举的功能
 * 
 * @author richie696
 * @version 2.0
 * @since 2025-11-01
 */
export abstract class Enum<T extends Enum<T>> {
    private static readonly enums: Map<string, Enum<any>[]> = new Map();

    protected constructor(private readonly objectName: string) {
        const className = this.constructor.name;
        if (!Enum.enums.has(className)) {
            Enum.enums.set(className, []);
        }
        Enum.enums.get(className)!.push(this);
    }

    /**
     * 获取当前类型所有的枚举类实例只读数组的函数
     * @return 返回当前类型所有的枚举类实例只读数组
     */
    public static values<T extends Enum<T>>(): ReadonlyArray<T> {
        const className = this.name;
        const instances = Enum.enums.get(className) || [];
        return Object.freeze(instances as T[]);
    }

    /**
     * 根据枚举名称获取枚举对象的函数
     * @param name 枚举名称
     * @return 返回枚举对象（如果名称无效则抛出异常）
     */
    public static nameOf<T extends Enum<T>>(name: string): T {
        const values: ReadonlyArray<T> = this.values();
        for (const obj of values) {
            if (obj.name === name) {
                return obj;
            }
        }
        throw new Error(`您访问的枚举名称无效，name = ${name}`);
    }

    /**
     * 获取当前枚举类型序号的函数
     * @return {number} 返回当前枚举类型序号
     */
    public ordinal(): number {
        const className = this.constructor.name;
        const instances = Enum.enums.get(className) || [];
        return instances.indexOf(this);
    }

    /**
     * 将当前枚举类型转换为字符串的函数
     * @return {string} 返回当前枚举类型的名称
     */
    public toString(): string {
        return this.name;
    }

    /**
     * 获取当前枚举类型名称的函数
     * @return {string} 返回当前枚举类型的名称
     */
    get name(): string {
        return this.objectName;
    }
}

