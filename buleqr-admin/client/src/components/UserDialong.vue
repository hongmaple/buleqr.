<template>
  <div class="nofind">
    <el-dialog
      :title="dialong.title"
      type="primary"
      size="small"
      :close-on-press-escape="false"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :visible.sync="dialong.show"
    >
      <el-form :model="form" ref="formdoalog" :rules="formdialog" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <span>{{form.username}}</span>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <span>{{form.email}}</span>
        </el-form-item>
        <el-form-item label="密码" prop="state">
          <el-input type='password' v-model="form.password"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="dialong.show = false">取 消</el-button>
        <el-button type="primary" @click="addHandle('formdoalog')">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
// @ is an alias to /src
export default {
  name: "UserDialong",
  data() {
    return {
      formdialog: {
        username: [{ required: true, message: "用户名不能为空", trigger: "blur" }],
        email: [{ required: true, message: "邮箱不能为空", trigger: "blur" }]
      }
    };
  },
  props: {
    dialong: Object,
    form: {
       password: null,
       email: null,
       username: null,
       id: null
    },
    id: null
  },
  methods: {
    addHandle(formdoalog) {
      this.$refs[formdoalog].validate(valid => {
        if (valid) {
          if(this.dialong.option == "add") {
            this.$axios.post("/api/user/adminRegister", this.form).then(res => {
                this.$message({
                  type: "success",
                  message: "数据添加成功"
                });
                (this.dialong.show = false);
                this.$emit("UserData");
                //清空内容
                this.form = "";
            });
          }else {
            const formData = this.form;
            formData.id = this.id;
            this.$axios.put("/api/user/admin", formData,{headers: {"token": localStorage.getItem("eleToken")}}).then(res => {
                this.$message({
                  type: "success",
                  message: "数据修改成功"
                });
                (this.dialong.show = false);
                this.$emit("UserData");
                //清空内容
                this.form = "";
            });
          }
        } else {
          console.log("error submit!!");
          return false;
        }
      });
    }
  }
};
</script>
<style scoped>
</style>
