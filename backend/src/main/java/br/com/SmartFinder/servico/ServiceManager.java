package br.com.SmartFinder.servico;

import java.util.HashMap;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import br.com.SmartFinder.dados.IDeviceRepository;
import br.com.SmartFinder.dados.IUserRepository;
import br.com.SmartFinder.modelos.Device;
import br.com.SmartFinder.modelos.DeviceRequestDto;
import br.com.SmartFinder.modelos.LoginRequest;
import br.com.SmartFinder.modelos.ResponseMessage;
import br.com.SmartFinder.modelos.User;
import br.com.SmartFinder.modelos.UserRequestDto;
import jakarta.transaction.Transactional;

@Service
public class ServiceManager {

    private final ResponseMessage response;

    private final UserMapper userMapper;
    private final DeviceMapper deviceMapper;
    private final IUserRepository repositorioUI;
    private final IDeviceRepository repositorioDI;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private User usuarioLogado;

    public ServiceManager(UserMapper userMapper, DeviceMapper deviceMapper, IDeviceRepository repositorioDI, IUserRepository repositorioUI, ResponseMessage response) {

        this.userMapper = userMapper;
        this.deviceMapper = deviceMapper;
        this.repositorioDI = repositorioDI;
        this.repositorioUI = repositorioUI;
        this.response = response;
        //this.usuarioLogado = null;

        //Usado para testagem, apagar antes de por em produção
        this.usuarioLogado = this.repositorioUI.findByLogin("g").orElse(null);

    }

    public ResponseEntity<?> initializeSession(LoginRequest uDto) {

        if (uDto.login().isEmpty() || uDto.senha().isEmpty()) {
            this.response.setMensagem("É necessário preencher todos os campos para efetuar login");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);
        }

        Optional<User> optionalUser = this.repositorioUI.findByLogin(uDto.login());

        if (optionalUser.isEmpty()) {

            this.response.setMensagem("Não foi encontrado usuário com os dados informados");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        User userRep = optionalUser.get();

        if (!this.passwordEncoder.matches(uDto.senha(), userRep.getSenha())) {

            this.response.setMensagem("Senha incorreta, tente novamente");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        this.usuarioLogado = userRep;

        return new ResponseEntity<>(this.usuarioLogado, HttpStatus.ACCEPTED);

    }

    public ResponseEntity<?> finalizeSession() {

        this.usuarioLogado = null;
        this.response.setMensagem("Sessão finalizada com sucesso");
        return new ResponseEntity<>(this.response, HttpStatus.ACCEPTED);

    }

    public ResponseEntity<?> saveLoggedUser(User u){

        if(this.usuarioLogado == null){

            this.response.setMensagem("Não há usuário logado no sistema");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        if(!this.repositorioUI.existsById(u.getId())){

            this.response.setMensagem("Usuario fornecido não possui o id compatível");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        this.usuarioLogado = u;
        this.usuarioLogado.setSenha(this.passwordEncoder.encode(u.getSenha()));
        this.repositorioUI.save(this.usuarioLogado);
        return new ResponseEntity<>(u,HttpStatus.OK);


    }

    public ResponseEntity<?> selectLoggedUser() {

        if (this.usuarioLogado != null) {

            return new ResponseEntity<>(this.usuarioLogado, HttpStatus.OK);

        }

        this.response.setMensagem("Não há usuário logado no sistema");
        return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

    }

    public ResponseEntity<?> selectUserById(Long id) {

        if (this.repositorioUI.countById(id) == 0) {

            this.response.setMensagem("Não há usuário com o id informado");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        User u = repositorioUI.findById(id).orElse(null);

        return new ResponseEntity<>(userMapper.userToDtoResponse(u), HttpStatus.OK);

    }

    public ResponseEntity<?> registerUser(UserRequestDto uDto) {

        if (uDto.login().isEmpty() || uDto.senha().isEmpty() || uDto.cpf().isEmpty() || uDto.email().isEmpty()) {
            this.response.setMensagem("É necessário preencher todos os campos para efetuar o cadastro");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);
        }

        if (this.repositorioUI.existsByLogin(uDto.login()) || this.repositorioUI.existsByCpf(uDto.cpf())) {

            this.response.setMensagem("Usuário já cadastrado com esse login ou cpf");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);
        }

        //Mapeamento,registro no repositorio e resposta para o usuário
        User u = userMapper.dtoToUser(uDto);
        u.setSenha(passwordEncoder.encode(u.getSenha()));
        this.repositorioUI.save(u);
        u = this.repositorioUI.findByLogin(u.getLogin()).orElse(null);

        return new ResponseEntity<>(u, HttpStatus.CREATED);

    }

    public ResponseEntity<?> listUsers() {

        //Mapeia e retorna uma lista dos usuarios do repositorio
        //return new ResponseEntity<>(this.repositorioUI.findAll().stream().map(u -> userMapper.userToDtoResponse(u)).collect(Collectors.toList()), HttpStatus.OK);
        //método para testes e depuração, apagar em produção
        return new ResponseEntity<>(this.repositorioUI.findAll(), HttpStatus.OK);
    }

    public ResponseEntity<?> editUser(UserRequestDto userDto) {

        if (!this.repositorioUI.existsByLogin(userDto.login())) {
            this.response.setMensagem("Não foi encontrado usuário com os dados informados");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);
        }

        User u = this.repositorioUI.findByLogin(userDto.login()).orElse(null);

        u.setLogin(userDto.login());
        u.setSenha(this.passwordEncoder.encode(userDto.senha()));
        u.setCpf(userDto.cpf());
        u.setEmail(userDto.email());

        this.repositorioUI.save(u);

        u = this.repositorioUI.findByLogin(u.getLogin()).orElse(null);
        return new ResponseEntity<>(u, HttpStatus.OK);

    }

    @Transactional
    public ResponseEntity<?> removeUser(Long id) {

        if (this.repositorioUI.countById(id) == 0) {

            this.response.setMensagem("Não há usuários com o id informado");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        Optional<User> obj = this.repositorioUI.findById(id);

        User u = obj.get();

        u.getDispositivos().forEach(a -> {

            this.repositorioDI.delete(a);

        });

        if (this.usuarioLogado != null && u.equals(this.usuarioLogado)) {

            this.usuarioLogado = null;

        }

        this.repositorioUI.delete(u);

        return new ResponseEntity<>(u, HttpStatus.OK);

    }

    public ResponseEntity<?> selectDeviceById(Long id) {

        if (this.repositorioDI.countById(id) == 0) {

            this.response.setMensagem("Não há dispositivo com o id informado");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        Device d = this.repositorioDI.findById(id).orElse(null);

        return new ResponseEntity<>(deviceMapper.deviceToDeviceRequestDto(d), HttpStatus.OK);

    }

    @Transactional
    public ResponseEntity<?> registerDevice(DeviceRequestDto dDto) {

        if (dDto.nome().equals("")) {

            this.response.setMensagem("O nome não pode estar em branco");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        Device d = deviceMapper.dtoToDevice(dDto);

        if (this.repositorioDI.existsByNome(dDto.nome())) {

            this.response.setMensagem("Dispositivo já cadastrado");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        d.setUser(this.usuarioLogado);
        d.setId(0L);
        


        this.repositorioDI.save(d);

        d = this.repositorioDI.findByNome(d.getNome()).orElse(null);
        this.usuarioLogado.getDispositivos().add(d);
        this.repositorioUI.save(this.usuarioLogado);

        System.out.println(d.getId());

        return new ResponseEntity<>(d, HttpStatus.CREATED);

    }

    public ResponseEntity<?> listDevices() {

        //return new ResponseEntity<>(this.repositorioDI.findAll().stream().map(d -> deviceMapper.deviceToDtoReponse(d)), HttpStatus.OK);
        return new ResponseEntity<>(this.repositorioDI.findAll(), HttpStatus.OK);

    }

    public ResponseEntity<?> editDevice(DeviceRequestDto dDto) {

        if (dDto.nome().equals("")) {

            this.response.setMensagem("O nome não pode estar em branco");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }
        
        if (this.repositorioDI.countByNome(dDto.nome()) == 0) {

            this.response.setMensagem("Não há dispositivos com o id informado");
            return new ResponseEntity<>(this.response, HttpStatus.NOT_FOUND);
        }

        Device d = this.repositorioDI.findByNome(dDto.nome()).orElse(null);


        if (this.repositorioDI.countByNome(dDto.nome()) != 0) {

            this.response.setMensagem("Esse nome de dispositivo já está em uso");
            return new ResponseEntity<>(this.response, HttpStatus.NOT_FOUND);

        }

        d.setNome(dDto.nome());

        this.repositorioDI.save(d);

        return new ResponseEntity<>(d, HttpStatus.OK);

    }

    @Transactional
    public ResponseEntity<?> removeDevice(Long id) {

        if (this.repositorioDI.countById(id) == 0) {

            this.response.setMensagem("Não há dispositivo com o id informado");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);
        }

        Optional<Device> obj = this.repositorioDI.findById(id);

        Device d = obj.get();

        this.usuarioLogado.getDispositivos().remove(d);

        d.setUser(null);

        this.repositorioDI.delete(d);

        System.out.println(d.getId());

        return new ResponseEntity<>(d, HttpStatus.OK);

    }

    public ResponseEntity<?> listUserDevices(Long id){

        if (this.repositorioUI.countById(id) == 0) {

            this.response.setMensagem("Não há usuários com o id informado");
            return new ResponseEntity<>(this.response, HttpStatus.BAD_REQUEST);

        }

        Optional<User> obj = this.repositorioUI.findById(id);

        User u = obj.get();

        return new ResponseEntity<>(u.getDispositivos(),HttpStatus.OK);
        

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException exp) {

        HashMap<String, String> erros = new HashMap<>();

        exp.getBindingResult().getAllErrors().forEach(e -> {

            String nome = ((FieldError) e).getField();
            String mensagem = e.getDefaultMessage();
            erros.put(nome, mensagem);

        });

        return new ResponseEntity<>(erros, HttpStatus.BAD_REQUEST);

    }

}
