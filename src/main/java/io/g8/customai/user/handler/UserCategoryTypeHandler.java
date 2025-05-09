package io.g8.customai.user.handler;

import io.g8.customai.user.entity.User;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户类别枚举类型处理器
 */
public class UserCategoryTypeHandler extends BaseTypeHandler<User.Category> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, User.Category parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public User.Category getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return rs.wasNull() ? null : User.Category.valueOf(value);
    }

    @Override
    public User.Category getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return rs.wasNull() ? null : User.Category.valueOf(value);
    }

    @Override
    public User.Category getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return cs.wasNull() ? null : User.Category.valueOf(value);
    }
}